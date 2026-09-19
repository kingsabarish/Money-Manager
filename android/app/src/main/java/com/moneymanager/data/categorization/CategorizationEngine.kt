package com.moneymanager.data.categorization

import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.CategoryMlWeightDao
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
         * Extracts ML features from transaction inputs.
         */
        fun extractFeatures(
            merchant: String,
            note: String?,
            amount: BigDecimal,
            time: LocalTime = LocalTime.now(),
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

            // 3. Time-of-day bucket
            val timeBucket =
                when (time.hour) {
                    in 5..11 -> "morning"
                    in 12..16 -> "afternoon"
                    in 17..20 -> "evening"
                    in 21..23, 0 -> "night"
                    else -> "late_night"
                }
            features.add("time:$timeBucket")

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
         * Predicts the most suitable category.
         * Top-level category matching is mandatory (fallback: "Other" / "Others").
         * Subcategory matching is optional and assigned only if confidence is high.
         */
        suspend fun predictCategory(
            merchant: String,
            note: String?,
            amount: BigDecimal,
            time: LocalTime = LocalTime.now(),
            date: LocalDate = LocalDate.now(),
        ): CategorizationResult {
            val allCategories = categoryDao.getAll().map { Category(it.id, it.name, it.parentId, it.type) }
            val topCategories = allCategories.filter { it.isTopLevel }

            if (topCategories.isEmpty()) {
                // If database has no categories seeded, return a dummy fallback
                return CategorizationResult(0L, null, 0L, 0f)
            }

            // Fallback category: look for "Other" or "Others"
            val fallbackCategory =
                topCategories.firstOrNull {
                    it.name.equals("Other", ignoreCase = true) || it.name.equals("Others", ignoreCase = true)
                } ?: topCategories.first()

            val features = extractFeatures(merchant, note, amount, time, date)
            val weights = mlWeightDao.getWeightsForFeatures(features)

            // 1. Score Top-Level Categories
            val topScores = mutableMapOf<Long, Float>()
            for (top in topCategories) {
                // Base Laplace smoothing prior
                var score = 1.0f

                // Direct top category weights + sum of subcategory weights under this parent
                val subIds = allCategories.filter { it.parentId == top.id }.map { it.id }.toSet()
                val targetIds = subIds + top.id

                for (w in weights) {
                    if (w.categoryId in targetIds) {
                        // Weighted boost: merchant matches carry 5x weight over time/day signals
                        val multiplier = if (w.featureKey.startsWith("merchant:")) 5.0f else 1.0f
                        score += w.count * multiplier
                    }
                }
                topScores[top.id] = score
            }

            // Best top category
            val bestTopEntry = topScores.maxByOrNull { it.value }
            val chosenTopId =
                if (bestTopEntry != null && bestTopEntry.value > 1.5f) {
                    bestTopEntry.key
                } else {
                    fallbackCategory.id
                }

            // 2. Score Subcategories (Optional) under chosen top category
            val availableSubcategories = allCategories.filter { it.parentId == chosenTopId }
            var chosenSubId: Long? = null
            var subConfidence = 0.0f

            if (availableSubcategories.isNotEmpty()) {
                val subScores = mutableMapOf<Long, Float>()
                for (sub in availableSubcategories) {
                    var score = 0.0f
                    for (w in weights) {
                        if (w.categoryId == sub.id) {
                            val multiplier =
                                when {
                                    w.featureKey.startsWith("merchant:") -> 4.0f
                                    w.featureKey.startsWith("token:") -> 3.0f
                                    w.featureKey.startsWith("time:") -> 2.0f
                                    else -> 1.0f
                                }
                            score += w.count * multiplier
                        }
                    }
                    if (score > 0f) {
                        subScores[sub.id] = score
                    }
                }

                val bestSub = subScores.maxByOrNull { it.value }
                // Assign subcategory only if there is a confident signal (score >= 2.0)
                if (bestSub != null && bestSub.value >= 2.0f) {
                    chosenSubId = bestSub.key
                    subConfidence = bestSub.value
                }
            }

            val finalId = chosenSubId ?: chosenTopId
            val confidence = bestTopEntry?.value ?: 1.0f

            return CategorizationResult(
                topCategoryId = chosenTopId,
                subCategoryId = chosenSubId,
                finalCategoryId = finalId,
                confidence = confidence + subConfidence,
            )
        }

        /**
         * Online training: updates ML weights based on approved or user-edited category.
         */
        suspend fun train(
            merchant: String,
            note: String?,
            amount: BigDecimal,
            time: LocalTime = LocalTime.now(),
            date: LocalDate = LocalDate.now(),
            assignedCategoryId: Long,
        ) {
            val allCategories = categoryDao.getAll()
            val assigned = allCategories.firstOrNull { it.id == assignedCategoryId } ?: return

            val features = extractFeatures(merchant, note, amount, time, date)
            val now = System.currentTimeMillis()

            for (feat in features) {
                // Train assigned category
                mlWeightDao.incrementWeight(feat, assigned.id, now)

                // If assigned category is a subcategory, also reinforce its parent top category
                if (assigned.parentId != null) {
                    mlWeightDao.incrementWeight(feat, assigned.parentId, now)
                }
            }
        }

        private fun cleanFeatureToken(str: String): String =
            str.lowercase().replace(Regex("""[^a-z0-9]+"""), "").trim()

        private companion object {
            val STOP_WORDS =
                setOf(
                    "and", "the", "for", "with", "from", "paid", "sent", "requested",
                    "bill", "order", "money", "payment", "upi", "vpa", "ref", "txn",
                )
        }
    }

