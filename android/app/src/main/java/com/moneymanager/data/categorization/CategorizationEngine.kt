package com.moneymanager.data.categorization

import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.CategoryMlWeightDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.CategoryMlWeightEntity
import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.domain.model.Category
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class CategorizationResult(
    val topCategoryId: Long,
    val subCategoryId: Long?,
    val finalCategoryId: Long, // subCategoryId ?: topCategoryId
    val confidence: Float,
)

@Singleton
class CategorizationEngine
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
        private val mlWeightDao: CategoryMlWeightDao,
    ) {
        /**
         * Cleans a category name by removing emojis, punctuation, and non-alphanumerics.
         */
        fun cleanCategoryName(name: String): String =
            name.lowercase().replace(Regex("""[^a-z0-9\s/]"""), "").trim()

        /**
         * Extracts ML features from transaction inputs.
         */
        fun extractFeatures(
            merchant: String,
            note: String?,
            amount: BigDecimal,
            time: LocalTime? = LocalTime.now(),
            date: LocalDate = LocalDate.now(),
        ): List<String> {
            val features = mutableListOf<String>()

            // 1. Merchant feature
            val cleanMerchant = cleanFeatureToken(merchant)
            if (cleanMerchant.isNotBlank()) {
                features.add("merchant:$cleanMerchant")
            }

            // 2. Note and group title tokens
            if (!note.isNullOrBlank()) {
                val words = note.lowercase().split(Regex("""[^a-z0-9]+"""))
                for (w in words) {
                    val clean = w.trim()
                    if (clean.length in 3..25 && !STOP_WORDS.contains(clean)) {
                        features.add("token:$clean")
                    }
                }
            }

            // 3. Time-of-day bucket (if known)
            if (time != null) {
                val timeBucket =
                    when (time.hour) {
                        in 5..11 -> "morning"
                        in 12..16 -> "afternoon"
                        in 17..20 -> "evening"
                        in 21..23, 0 -> "night"
                        else -> "late_night"
                    }
                features.add("time:$timeBucket")
            }

            // 4. Day of week bucket
            val dayBucket =
                if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                    "weekend"
                } else {
                    "weekday"
                }
            features.add("day:$dayBucket")

            // 5. Amount bracket
            val amountBucket =
                when {
                    amount < BigDecimal("200") -> "micro"
                    amount <= BigDecimal("1000") -> "small"
                    amount <= BigDecimal("5000") -> "medium"
                    else -> "large"
                }
            features.add("amount:$amountBucket")

            return features
        }

        /**
         * Predicts the most suitable category and subcategory following strict priority:
         * 1. Priority 1 (Highest): Explicit category in group / note (e.g. "Food - ...").
         *    Locked to this category immediately. Subcategory is checked strictly for explicit
         *    meal/sub keywords. Generic items stay at the top category.
         * 2. Priority 2 (Medium): User transaction history & online learned weights (merchant / payee / trained tokens).
         * 3. Priority 3 (Lowest / Fallback): Seeded keyword dictionary & business suffix heuristics.
         */
        suspend fun predictCategory(
            merchant: String,
            note: String?,
            amount: BigDecimal,
            time: LocalTime? = LocalTime.now(),
            date: LocalDate = LocalDate.now(),
        ): CategorizationResult {
            val allCategories = categoryDao.getAll().map { Category(it.id, it.name, it.parentId, it.type) }
            val topCategories = allCategories.filter { it.isTopLevel }

            if (topCategories.isEmpty()) {
                return CategorizationResult(0L, null, 0L, 0f)
            }

            val fallbackCategory =
                topCategories.firstOrNull {
                    val clean = cleanCategoryName(it.name)
                    clean == "other" || clean == "others"
                } ?: topCategories.first()

            val noteText = note.orEmpty()
            val combinedText = "$merchant $noteText".lowercase()
            val textTokens = combinedText.split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }

            // =========================================================================
            // PRIORITY 1: Explicit Category in Group or Note (Direct Category Match)
            // =========================================================================
            var explicitTopCategory: Category? = null
            for (top in topCategories) {
                val cleanTop = cleanCategoryName(top.name)
                if (cleanTop.length < 3) continue
                val pattern = Regex("""\b${Regex.escape(cleanTop)}\b""")
                if (pattern.containsMatchIn(combinedText)) {
                    explicitTopCategory = top
                    break
                }
            }

            if (explicitTopCategory != null) {
                val topId = explicitTopCategory.id
                val subcategories = allCategories.filter { it.parentId == topId }
                var matchedSubId: Long? = null

                if (subcategories.isNotEmpty()) {
                    // Check direct subcategory name (e.g. "dinner", "lunch", "petrol")
                    for (sub in subcategories) {
                        val cleanSub = cleanCategoryName(sub.name)
                        val subWords = cleanSub.split(Regex("""\s+""")).filter { it.length >= 3 }
                        val matchesSubName = subWords.any { word ->
                            Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(combinedText)
                        }
                        if (matchesSubName) {
                            matchedSubId = sub.id
                            break
                        }
                    }

                    // If subcategory name not directly found, check explicit subcategory keywords
                    if (matchedSubId == null) {
                        for (token in textTokens) {
                            val target = KEYWORDS_MAP[token] ?: continue
                            if (target.subcategoryName != null) {
                                val cleanTargetSub = cleanCategoryName(target.subcategoryName)
                                val matched = subcategories.firstOrNull {
                                    val currentClean = cleanCategoryName(it.name)
                                    currentClean == cleanTargetSub ||
                                        (cleanTargetSub.contains("cab") && (currentClean.contains("cab") || currentClean.contains("auto"))) ||
                                        (cleanTargetSub.contains("public transport") && (currentClean.contains("transport") || currentClean.contains("bus")))
                                }
                                if (matched != null) {
                                    matchedSubId = matched.id
                                    break
                                }
                            }
                        }
                    }
                }

                // If note has generic item (e.g. "Food - for-eggs"), matchedSubId is null -> stays top-level Food!
                val finalId = matchedSubId ?: topId
                return CategorizationResult(
                    topCategoryId = topId,
                    subCategoryId = matchedSubId,
                    finalCategoryId = finalId,
                    confidence = 100.0f,
                )
            }

            // =========================================================================
            // PRIORITY 2: User Transaction History / Online Learned Model
            // =========================================================================
            val features = extractFeatures(merchant, note, amount, time, date)
            val weights = mlWeightDao.getWeightsForFeatures(features)

            // Check if there are user learned weights for merchant or tokens
            val historyWeights = weights.filter {
                it.featureKey.startsWith("merchant:") || it.featureKey.startsWith("token:")
            }

            if (historyWeights.isNotEmpty()) {
                val historyScores = mutableMapOf<Long, Float>()
                for (top in topCategories) {
                    var score = 0.0f
                    val subIds = allCategories.filter { it.parentId == top.id }.map { it.id }.toSet()
                    val targetIds = subIds + top.id

                    for (w in historyWeights) {
                        if (w.categoryId in targetIds) {
                            val multiplier = if (w.featureKey.startsWith("merchant:")) 10.0f else 4.0f
                            score += w.count * multiplier
                        }
                    }
                    if (score > 0f) {
                        historyScores[top.id] = score
                    }
                }

                val bestHistoryEntry = historyScores.maxByOrNull { it.value }
                if (bestHistoryEntry != null && bestHistoryEntry.value >= 3.0f) {
                    val chosenTopId = bestHistoryEntry.key
                    val subcategories = allCategories.filter { it.parentId == chosenTopId }
                    var chosenSubId: Long? = null

                    if (subcategories.isNotEmpty()) {
                        val subScores = mutableMapOf<Long, Float>()
                        for (sub in subcategories) {
                            var score = 0.0f
                            for (w in historyWeights) {
                                if (w.categoryId == sub.id) {
                                    val multiplier = if (w.featureKey.startsWith("merchant:")) 10.0f else 4.0f
                                    score += w.count * multiplier
                                }
                            }
                            if (score > 0f) {
                                subScores[sub.id] = score
                            }
                        }
                        val bestSub = subScores.maxByOrNull { it.value }
                        if (bestSub != null && bestSub.value >= 3.0f) {
                            chosenSubId = bestSub.key
                        }
                    }

                    val finalId = chosenSubId ?: chosenTopId
                    return CategorizationResult(
                        topCategoryId = chosenTopId,
                        subCategoryId = chosenSubId,
                        finalCategoryId = finalId,
                        confidence = 50.0f + bestHistoryEntry.value,
                    )
                }
            }

            // =========================================================================
            // PRIORITY 3: Seeded Keyword Dictionary & Fallback Heuristics
            // =========================================================================
            // 1. Direct subcategory name in text
            for (cat in allCategories.filter { !it.isTopLevel }) {
                val cleanSub = cleanCategoryName(cat.name)
                val words = cleanSub.split(Regex("""\s+""")).filter { it.length >= 3 }
                if (words.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(combinedText) }) {
                    val parentId = cat.parentId ?: continue
                    return CategorizationResult(
                        topCategoryId = parentId,
                        subCategoryId = cat.id,
                        finalCategoryId = cat.id,
                        confidence = 40.0f,
                    )
                }
            }

            // 2. Keyword Dictionary
            for (token in textTokens) {
                val target = KEYWORDS_MAP[token] ?: continue
                val cleanTop = cleanCategoryName(target.categoryName)
                val topCat = topCategories.firstOrNull { cleanCategoryName(it.name) == cleanTop }
                if (topCat != null) {
                    var subCatId: Long? = null
                    if (target.subcategoryName != null) {
                        val cleanSub = cleanCategoryName(target.subcategoryName)
                        subCatId = allCategories.firstOrNull {
                            val curClean = cleanCategoryName(it.name)
                            it.parentId == topCat.id &&
                                (curClean == cleanSub ||
                                    (cleanSub.contains("cab") && (curClean.contains("cab") || curClean.contains("auto"))) ||
                                    (cleanSub.contains("public transport") && (curClean.contains("transport") || curClean.contains("bus"))))
                        }?.id
                    }
                    val finalId = subCatId ?: topCat.id
                    return CategorizationResult(
                        topCategoryId = topCat.id,
                        subCategoryId = subCatId,
                        finalCategoryId = finalId,
                        confidence = 30.0f,
                    )
                }
            }

            // 3. Business Suffix Heuristics
            for (token in textTokens) {
                val suffixCat = when {
                    FOOD_SUFFIXES.any { token.endsWith(it) } -> "food"
                    TRAVEL_SUFFIXES.any { token.endsWith(it) } -> "travel"
                    HEALTH_SUFFIXES.any { token.endsWith(it) } -> "health"
                    APPAREL_SUFFIXES.any { token.endsWith(it) } -> "apparel"
                    HOUSEHOLD_SUFFIXES.any { token.endsWith(it) } -> "household"
                    else -> null
                }
                if (suffixCat != null) {
                    val topCat = topCategories.firstOrNull { cleanCategoryName(it.name) == suffixCat }
                    if (topCat != null) {
                        return CategorizationResult(
                            topCategoryId = topCat.id,
                            subCategoryId = null,
                            finalCategoryId = topCat.id,
                            confidence = 20.0f,
                        )
                    }
                }
            }

            // 4. Default Fallback
            return CategorizationResult(
                topCategoryId = fallbackCategory.id,
                subCategoryId = null,
                finalCategoryId = fallbackCategory.id,
                confidence = 1.0f,
            )
        }

        /**
         * Online training: updates ML weights based on approved or user-edited category.
         */
        suspend fun train(
            merchant: String,
            note: String?,
            amount: BigDecimal,
            time: LocalTime? = LocalTime.now(),
            date: LocalDate = LocalDate.now(),
            assignedCategoryId: Long,
        ) {
            val allCategories = categoryDao.getAll()
            val assigned = allCategories.firstOrNull { it.id == assignedCategoryId } ?: return

            val features = extractFeatures(merchant, note, amount, time, date)
            val now = System.currentTimeMillis()

            for (feat in features) {
                mlWeightDao.incrementWeight(feat, assigned.id, now)
                if (assigned.parentId != null) {
                    mlWeightDao.incrementWeight(feat, assigned.parentId, now)
                }
            }
        }

        /**
         * Seeds ML weights from pre-packaged historical weights matching existing categories.
         */
        suspend fun seedInitialWeights() {
            if (mlWeightDao.getCount() > 0) return
            val allCategories = categoryDao.getAll()
            if (allCategories.isEmpty()) return

            val cleanToCategory = allCategories.associateBy { cleanCategoryName(it.name) }
            val now = System.currentTimeMillis()
            val weightEntities = mutableListOf<CategoryMlWeightEntity>()

            for (item in InitialMlWeights.WEIGHTS) {
                val cleanName = cleanCategoryName(item.categoryName)
                val cat = cleanToCategory[cleanName]
                    ?: allCategories.firstOrNull {
                        val c = cleanCategoryName(it.name)
                        (cleanName.contains("cab") && (c.contains("cab") || c.contains("auto"))) ||
                            (cleanName.contains("public transport") && (c.contains("transport") || c.contains("bus")))
                    }
                if (cat != null) {
                    weightEntities.add(
                        CategoryMlWeightEntity(
                            featureKey = item.featureKey,
                            categoryId = cat.id,
                            count = item.count,
                            lastUpdatedEpochMs = now,
                        ),
                    )
                }
            }
            if (weightEntities.isNotEmpty()) {
                mlWeightDao.insertAll(weightEntities)
            }
        }

        /**
         * Seeds ML weights directly from a list of transactions and categories (e.g. on backup restore).
         */
        suspend fun seedFromTransactions(
            transactions: List<TransactionEntity>,
            categories: List<CategoryEntity>,
        ) {
            if (transactions.isEmpty() || categories.isEmpty()) return
            val categoryById = categories.associateBy { it.id }
            val now = System.currentTimeMillis()

            val counts = mutableMapOf<Pair<String, Long>, Int>()

            for (t in transactions) {
                val cat = categoryById[t.categoryId] ?: continue
                val features =
                    extractFeatures(
                        merchant = t.merchant ?: "",
                        note = t.note,
                        amount = t.amount,
                        time = null,
                        date = t.date,
                    )
                val targets = mutableListOf(cat.id)
                if (cat.parentId != null) {
                    targets.add(cat.parentId)
                }

                for (f in features) {
                    for (targetId in targets) {
                        val pair = Pair(f, targetId)
                        counts[pair] = (counts[pair] ?: 0) + 1
                    }
                }
            }

            val entities =
                counts.map { (pair, count) ->
                    CategoryMlWeightEntity(
                        featureKey = pair.first,
                        categoryId = pair.second,
                        count = count,
                        lastUpdatedEpochMs = now,
                    )
                }

            mlWeightDao.insertAll(entities)
        }

        private fun cleanFeatureToken(str: String): String =
            str.lowercase().replace(Regex("""[^a-z0-9]+"""), "").trim()

        private data class KeywordTarget(val categoryName: String, val subcategoryName: String? = null)

        private companion object {
            val STOP_WORDS =
                setOf(
                    "and", "the", "for", "with", "from", "paid", "sent", "requested",
                    "bill", "order", "money", "payment", "upi", "vpa", "ref", "txn",
                )

            val FOOD_SUFFIXES = listOf("foods", "restaurant", "hotel", "kitchen", "bakes", "chaat", "sweets", "mess", "canteen", "bakery")
            val TRAVEL_SUFFIXES = listOf("travels", "fuels", "fuel", "fue", "petroleum", "motors")
            val HEALTH_SUFFIXES = listOf("pharmacy", "pharma", "clinic", "hospital")
            val APPAREL_SUFFIXES = listOf("textiles", "silks", "fashions", "trends", "menswear")
            val HOUSEHOLD_SUFFIXES = listOf("mart", "supermarket", "provisions", "stores")

            val KEYWORDS_MAP =
                mapOf(
                    // Food - Top Level Only (Generic food items, ingredients, delivery, restaurants)
                    "swiggy" to KeywordTarget("Food"),
                    "zomato" to KeywordTarget("Food"),
                    "egg" to KeywordTarget("Food"),
                    "eggs" to KeywordTarget("Food"),
                    "biryani" to KeywordTarget("Food"),
                    "burger" to KeywordTarget("Food"),
                    "pizza" to KeywordTarget("Food"),
                    "shawarma" to KeywordTarget("Food"),
                    "parotta" to KeywordTarget("Food"),
                    "maggi" to KeywordTarget("Food"),
                    "meals" to KeywordTarget("Food"),
                    "restaurant" to KeywordTarget("Food"),
                    "hotel" to KeywordTarget("Food"),
                    "kitchen" to KeywordTarget("Food"),
                    "mess" to KeywordTarget("Food"),
                    "canteen" to KeywordTarget("Food"),
                    "dhaba" to KeywordTarget("Food"),
                    "cafe" to KeywordTarget("Food"),
                    "dining" to KeywordTarget("Food"),

                    // Food - Explicit Meal Subcategories
                    "breakfast" to KeywordTarget("Food", "Breakfast"),
                    "tiffin" to KeywordTarget("Food", "Breakfast"),
                    "lunch" to KeywordTarget("Food", "Lunch"),
                    "dinner" to KeywordTarget("Food", "Dinner"),
                    "tea" to KeywordTarget("Food", "snacks"),
                    "coffee" to KeywordTarget("Food", "snacks"),
                    "snack" to KeywordTarget("Food", "snacks"),
                    "snacks" to KeywordTarget("Food", "snacks"),
                    "biscuit" to KeywordTarget("Food", "snacks"),
                    "samosa" to KeywordTarget("Food", "snacks"),
                    "juice" to KeywordTarget("Food", "snacks"),
                    "icecream" to KeywordTarget("Food", "snacks"),
                    "bakery" to KeywordTarget("Food", "snacks"),
                    "bakes" to KeywordTarget("Food", "snacks"),
                    "sweets" to KeywordTarget("Food", "snacks"),
                    "chaat" to KeywordTarget("Food", "snacks"),

                    // Travel - Top Level Only
                    "travel" to KeywordTarget("Travel"),
                    "travels" to KeywordTarget("Travel"),
                    "toll" to KeywordTarget("Travel"),
                    "fastag" to KeywordTarget("Travel"),
                    "parking" to KeywordTarget("Travel"),

                    // Travel - Subcategories (Cab/Auto combined, Public Transport, petrol, trip)
                    "cab" to KeywordTarget("Travel", "Cab / Auto"),
                    "auto" to KeywordTarget("Travel", "Cab / Auto"),
                    "rapido" to KeywordTarget("Travel", "Cab / Auto"),
                    "uber" to KeywordTarget("Travel", "Cab / Auto"),
                    "ola" to KeywordTarget("Travel", "Cab / Auto"),
                    "rickshaw" to KeywordTarget("Travel", "Cab / Auto"),

                    "bus" to KeywordTarget("Travel", "Public Transport"),
                    "redbus" to KeywordTarget("Travel", "Public Transport"),
                    "train" to KeywordTarget("Travel", "Public Transport"),
                    "irctc" to KeywordTarget("Travel", "Public Transport"),
                    "metro" to KeywordTarget("Travel", "Public Transport"),
                    "ticket" to KeywordTarget("Travel", "Public Transport"),

                    "petrol" to KeywordTarget("Travel", "petrol"),
                    "fuel" to KeywordTarget("Travel", "petrol"),
                    "diesel" to KeywordTarget("Travel", "petrol"),
                    "hpcl" to KeywordTarget("Travel", "petrol"),
                    "bpcl" to KeywordTarget("Travel", "petrol"),
                    "ioc" to KeywordTarget("Travel", "petrol"),
                    "shell" to KeywordTarget("Travel", "petrol"),
                    "trip" to KeywordTarget("Travel", "Trip"),

                    // Bills
                    "bill" to KeywordTarget("Bills"),
                    "bills" to KeywordTarget("Bills"),
                    "recharge" to KeywordTarget("Bills", "mobile recharge"),
                    "airtel" to KeywordTarget("Bills", "mobile recharge"),
                    "jio" to KeywordTarget("Bills", "mobile recharge"),
                    "vi" to KeywordTarget("Bills", "mobile recharge"),
                    "vodafone" to KeywordTarget("Bills", "mobile recharge"),
                    "bsnl" to KeywordTarget("Bills", "mobile recharge"),
                    "wifi" to KeywordTarget("Bills", "wifi bill"),
                    "broadband" to KeywordTarget("Bills", "wifi bill"),
                    "act" to KeywordTarget("Bills", "wifi bill"),
                    "fibernet" to KeywordTarget("Bills", "wifi bill"),
                    "electricity" to KeywordTarget("Bills", "EB bill"),
                    "power" to KeywordTarget("Bills", "EB bill"),
                    "tneb" to KeywordTarget("Bills", "EB bill"),
                    "bescom" to KeywordTarget("Bills", "EB bill"),
                    "eb" to KeywordTarget("Bills", "EB bill"),
                    "gas" to KeywordTarget("Bills", "gas"),
                    "indane" to KeywordTarget("Bills", "gas"),
                    "emi" to KeywordTarget("Bills", "EMI"),
                    "loan" to KeywordTarget("Bills", "EMI"),
                    "insurance" to KeywordTarget("Bills", "Medical Insurance"),
                    "lic" to KeywordTarget("Bills", "Medical Insurance"),
                    "policybazaar" to KeywordTarget("Bills", "Medical Insurance"),
                    "ps5" to KeywordTarget("Bills", "ps5 subscription"),
                    "playstation" to KeywordTarget("Bills", "ps5 subscription"),
                    "psn" to KeywordTarget("Bills", "ps5 subscription"),
                    "creditcard" to KeywordTarget("Bills", "credit card"),
                    "cred" to KeywordTarget("Bills", "credit card"),

                    // Household
                    "household" to KeywordTarget("Household"),
                    "zepto" to KeywordTarget("Household", "groceries"),
                    "blinkit" to KeywordTarget("Household", "groceries"),
                    "instamart" to KeywordTarget("Household", "groceries"),
                    "bigbasket" to KeywordTarget("Household", "groceries"),
                    "dmart" to KeywordTarget("Household", "groceries"),
                    "supermarket" to KeywordTarget("Household", "groceries"),
                    "vegetables" to KeywordTarget("Household", "groceries"),
                    "veggies" to KeywordTarget("Household", "groceries"),
                    "fruits" to KeywordTarget("Household", "groceries"),
                    "groceries" to KeywordTarget("Household", "groceries"),
                    "provision" to KeywordTarget("Household", "groceries"),
                    "water" to KeywordTarget("Household", "water cane"),
                    "aquaguard" to KeywordTarget("Household", "water cane"),

                    // Health
                    "health" to KeywordTarget("Health"),
                    "medical" to KeywordTarget("Health"),
                    "pharmacy" to KeywordTarget("Health", "Medicine"),
                    "pharma" to KeywordTarget("Health", "Medicine"),
                    "medicine" to KeywordTarget("Health", "Medicine"),
                    "meds" to KeywordTarget("Health", "Medicine"),
                    "apollo" to KeywordTarget("Health", "Medicine"),
                    "medplus" to KeywordTarget("Health", "Medicine"),
                    "netmeds" to KeywordTarget("Health", "Medicine"),
                    "hospital" to KeywordTarget("Health", "Hospital"),
                    "clinic" to KeywordTarget("Health", "Hospital"),
                    "doctor" to KeywordTarget("Health", "Hospital"),
                    "dental" to KeywordTarget("Health", "Hospital"),
                    "gym" to KeywordTarget("Health", "gym"),
                    "fitness" to KeywordTarget("Health", "gym"),
                    "sports" to KeywordTarget("Health", "sports"),
                    "badminton" to KeywordTarget("Health", "sports"),

                    // Entertainment
                    "entertainment" to KeywordTarget("Entertainment"),
                    "netflix" to KeywordTarget("Entertainment", "subscriptions"),
                    "hotstar" to KeywordTarget("Entertainment", "subscriptions"),
                    "spotify" to KeywordTarget("Entertainment", "subscriptions"),
                    "youtube" to KeywordTarget("Entertainment", "subscriptions"),
                    "bookmyshow" to KeywordTarget("Entertainment", "movie"),
                    "pvr" to KeywordTarget("Entertainment", "movie"),
                    "inox" to KeywordTarget("Entertainment", "movie"),
                    "cinema" to KeywordTarget("Entertainment", "movie"),
                    "theatre" to KeywordTarget("Entertainment", "movie"),
                    "movie" to KeywordTarget("Entertainment", "movie"),
                    "gaming" to KeywordTarget("Entertainment", "games"),
                    "games" to KeywordTarget("Entertainment", "games"),
                    "steam" to KeywordTarget("Entertainment", "games"),

                    // Apparel
                    "apparel" to KeywordTarget("Apparel"),
                    "shopping" to KeywordTarget("Apparel"),
                    "myntra" to KeywordTarget("Apparel", "Clothing"),
                    "ajio" to KeywordTarget("Apparel", "Clothing"),
                    "zara" to KeywordTarget("Apparel", "Clothing"),
                    "clothing" to KeywordTarget("Apparel", "Clothing"),
                    "fashion" to KeywordTarget("Apparel", "Fashion"),
                    "shoes" to KeywordTarget("Apparel", "Shoes"),
                    "footwear" to KeywordTarget("Apparel", "Shoes"),
                    "crocs" to KeywordTarget("Apparel", "Shoes"),
                    "saloon" to KeywordTarget("Apparel", "saloon"),
                    "salon" to KeywordTarget("Apparel", "saloon"),
                    "haircut" to KeywordTarget("Apparel", "saloon"),
                    "spa" to KeywordTarget("Apparel", "saloon"),
                )
        }
    }
