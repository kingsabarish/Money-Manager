package com.moneymanager.data.categorization

import android.util.Log
import com.moneymanager.data.ingestion.TransactionSource
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.TransactionType
import com.moneymanager.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class CategorizationResult(
    val topCategoryId: Long,
    val subCategoryId: Long?,
    val finalCategoryId: Long,
    val predictedNote: String,
    val predictedMerchant: String,
    val categorizationNotes: String? = null,
    val confidence: Float = 1.0f,
)

@Singleton
class CategorizationEngine @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val settingsRepository: SettingsRepository,
    private val cloudGeminiClient: CloudGeminiClient,
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * Cleans a category name by removing emojis, punctuation, and non-alphanumerics.
     */
    fun cleanCategoryName(name: String): String =
        name.lowercase().replace(Regex("""[^a-z0-9\s/]"""), "").trim()

    /**
     * Checks if Gemini Cloud AI is configured with a valid API key.
     */
    suspend fun isAiConfigured(): Boolean {
        val apiKey = settingsRepository.observe().first().geminiApiKey
        return !apiKey.isNullOrBlank()
    }

    /** Backward compatibility alias */
    suspend fun isAiCoreSupported(): Boolean = isAiConfigured()

    /**
     * Predicts the category, subcategory, user note, merchant, and AI categorization notes
     * using Cloud Gemini API (e.g. Gemini 2.5 Flash).
     *
     * It supplies the full 1.5-year historical transaction dataset formatted as TSV,
     * the category hierarchy tree, and the current transaction context in a single call.
     *
     * If unconfigured or if the request fails, it defaults to "Other" -> "General" with an empty note.
     */
    suspend fun predictCategory(
        rawMessage: String,
        source: TransactionSource,
        amount: BigDecimal,
        fallbackMerchant: String,
        fallbackNote: String? = null,
        time: LocalTime? = LocalTime.now(),
        date: LocalDate = LocalDate.now(),
    ): CategorizationResult {
        val allCategories = categoryDao.getAll()
        if (allCategories.isEmpty()) {
            val otherId = categoryDao.insert(
                CategoryEntity(name = "Other", parentId = null, type = TransactionType.EXPENSE)
            )
            val subId = categoryDao.insert(
                CategoryEntity(name = "General", parentId = otherId, type = TransactionType.EXPENSE)
            )
            return CategorizationResult(
                topCategoryId = otherId,
                subCategoryId = subId,
                finalCategoryId = subId,
                predictedNote = "",
                predictedMerchant = fallbackMerchant,
                categorizationNotes = null,
                confidence = 0f,
            )
        }

        val topCategories = allCategories.filter { it.parentId == null }
        val otherCategory = topCategories.firstOrNull {
            val clean = cleanCategoryName(it.name)
            clean == "other" || clean == "others"
        } ?: topCategories.first()

        val apiKey = settingsRepository.observe().first().geminiApiKey
        if (apiKey.isNullOrBlank()) {
            Log.i("CategorizationEngine", "Gemini API key is not configured; defaulting to Other -> General with empty note")
            return fallbackResult(otherCategory, allCategories, fallbackMerchant)
        }

        try {
            // Retrieve 1.5 years (548 days) of historical approved transactions
            val sinceDate = date.minusDays(548)
            val historyRecords = transactionDao.getHistorySince(sinceDate)
            Log.d("CategorizationEngine", "Retrieved ${historyRecords.size} history records since $sinceDate for Cloud Gemini context")

            val prompt = buildClassificationPrompt(
                rawMessage = rawMessage,
                source = source,
                amount = amount,
                allCategories = allCategories,
                historyRecords = historyRecords,
                time = time,
            )

            Log.d("CategorizationEngine", "Sending classification request to Cloud Gemini...")
            try {
                java.io.File("/data/data/com.moneymanager/cache/last_gemini_prompt.txt").writeText(prompt)
            } catch (_: Throwable) {}
            val result = cloudGeminiClient.generateContent(
                apiKey = apiKey,
                prompt = prompt,
                systemInstruction = "You are an expert personal finance categorization agent for Money Manager. Output strict JSON only.",
            )

            result.onSuccess { rawResponse ->
                Log.i("CategorizationEngine", "Cloud Gemini Response:\n$rawResponse")
                val prediction = parseClassificationResponse(rawResponse)
                if (prediction != null) {
                    return resolvePrediction(prediction, allCategories, fallbackMerchant, otherCategory, time)
                }
            }.onFailure { err ->
                Log.e("CategorizationEngine", "Cloud Gemini call failed", err)
            }
        } catch (e: Throwable) {
            Log.e("CategorizationEngine", "Categorization failed, defaulting to Other -> General", e)
        }

        return fallbackResult(otherCategory, allCategories, fallbackMerchant)
    }

    private fun buildClassificationPrompt(
        rawMessage: String,
        source: TransactionSource,
        amount: BigDecimal,
        allCategories: List<CategoryEntity>,
        historyRecords: List<com.moneymanager.data.local.dao.TransactionHistoryRecord>,
        time: LocalTime? = null,
    ): String {
        val topCategories = allCategories.filter { it.parentId == null }
        val categoryTree = topCategories.joinToString("\n") { top ->
            val children = allCategories.filter { it.parentId == top.id }
            val childNames = if (children.isNotEmpty()) {
                children.joinToString(", ") { it.name }
            } else {
                "General"
            }
            "- ${top.name}: $childNames"
        }

        val tsvHistory = if (historyRecords.isNotEmpty()) {
            buildString {
                appendLine("Date\tAmount\tMerchant\tCategory\tSubcategory\tNote\tAI_Notes")
                for (r in historyRecords) {
                    append(r.date).append('\t')
                    append("₹").append(r.amount).append('\t')
                    append(r.merchant).append('\t')
                    append(r.category).append('\t')
                    append(r.subcategory).append('\t')
                    append(r.note).append('\t')
                    appendLine(r.categorizationNotes.orEmpty())
                }
            }
        } else {
            "(No historical transactions available)"
        }

        val sourceDesc = when (source) {
            TransactionSource.SMS -> "Bank SMS"
            TransactionSource.GPAY_SPLIT -> "Google Pay"
        }

        val timeDesc = time?.let {
            val period = when {
                !it.isBefore(LocalTime.of(5, 0)) && it.isBefore(LocalTime.of(11, 30)) -> "Morning / Breakfast time (05:00 - 11:30)"
                !it.isBefore(LocalTime.of(11, 30)) && it.isBefore(LocalTime.of(16, 0)) -> "Midday / Lunch time (11:30 - 16:00)"
                !it.isBefore(LocalTime.of(16, 0)) && it.isBefore(LocalTime.of(19, 0)) -> "Late Afternoon / Snacks time (16:00 - 19:00)"
                else -> "Night / Dinner time (19:00 - 05:00)"
            }
            val formatted = it.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
            "$formatted ($period)"
        } ?: "Unknown"

        return """
        Analyze the incoming expense transaction and classify it accurately using the allowed category hierarchy and historical patterns.

        Incoming Transaction:
        - Source: $sourceDesc
        - Raw Message: "$rawMessage"
        - Amount: ₹${amount.toPlainString()}
        - Time of Day: $timeDesc

        Allowed Categories and Subcategories:
        $categoryTree

        Past 1.5 Years Transaction History (TSV format):
        $tsvHistory

        Instructions & Constraints:
        1. "merchant": The clean, recognizable payee/merchant/store name (e.g. "Swiggy", "Uber", "Amazon", "Apollo Pharmacy").
        2. "category": Must be one of the top-level categories listed in the allowed categories tree.
        3. "subcategory": Must be a valid subcategory for that category.
           - For "🍜 Food", select the meal subcategory (Breakfast, Lunch, snacks, Dinner) based on the Time of Day above and the user's historical patterns.
        4. "note": Follow the user's historical naming style closely (maximum 5 words!). Use concise descriptions (e.g. "Dinner", "Lunch", "Medicines", "Groceries", "Uber to airport"). Do NOT include merchant name or conjunctions (never write "Swiggy for Dinner"), and never include bank reference numbers, card numbers, or OTPs.
        5. "categorizationNotes": Brief AI context or reasoning explaining why this category/subcategory was chosen to guide future predictions (maximum 15 words!). Keep it concise to save storage.
        6. "Ground Truth Priority": In the Past History TSV, the Category, Subcategory, and Note are ground truth approved by the user. If an AI_Notes column conflicts with the Category/Subcategory, always prioritize the user's Category.

        Output strict JSON ONLY in this exact format:
        {
          "merchant": "<Merchant>",
          "category": "<Category>",
          "subcategory": "<Subcategory>",
          "note": "<Note>",
          "categorizationNotes": "<AI Notes>"
        }
        """.trimIndent()
    }

    private data class PredictionJson(
        val merchant: String,
        val category: String,
        val subcategory: String,
        val note: String,
        val categorizationNotes: String?,
    )

    private fun parseClassificationResponse(rawResponse: String): PredictionJson? {
        val trimmed = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        try {
            val json = JSONObject(trimmed)
            val cat = json.optString("category")
            if (cat.isNotBlank()) {
                val aiNotes = json.optString("categorizationNotes").ifBlank {
                    json.optString("categorizedNotes").ifBlank {
                        json.optString("aiNotes")
                    }
                }.trim().takeIf { it.isNotBlank() }

                return PredictionJson(
                    merchant = json.optString("merchant").trim(),
                    category = cat.trim(),
                    subcategory = json.optString("subcategory").trim(),
                    note = json.optString("note").trim(),
                    categorizationNotes = aiNotes,
                )
            }
        } catch (_: Exception) {
            // Fallback to regex if JSON formatting was slightly irregular
        }

        val cat = Regex(""""category"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
        if (cat.isNullOrBlank()) return null

        val merchant = Regex(""""merchant"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
        val sub = Regex(""""subcategory"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
        val note = Regex(""""note"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
        val aiNotes = Regex(""""(?:categorizationNotes|categorizedNotes|aiNotes)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()

        return PredictionJson(
            merchant = merchant.orEmpty(),
            category = cat,
            subcategory = sub.orEmpty(),
            note = note.orEmpty(),
            categorizationNotes = aiNotes?.takeIf { it.isNotBlank() },
        )
    }

    private suspend fun resolvePrediction(
        prediction: PredictionJson,
        allCategories: List<CategoryEntity>,
        fallbackMerchant: String,
        otherCategory: CategoryEntity,
        time: LocalTime? = null,
    ): CategorizationResult {
        val topCategories = allCategories.filter { it.parentId == null }
        val cleanPredCat = cleanCategoryName(prediction.category)

        val matchedTop = topCategories.firstOrNull {
            cleanCategoryName(it.name) == cleanPredCat
        } ?: topCategories.firstOrNull {
            val clean = cleanCategoryName(it.name)
            clean.contains(cleanPredCat) || cleanPredCat.contains(clean)
        } ?: otherCategory

        val isFoodCategory = cleanCategoryName(matchedTop.name) == "food"
        val timeBasedMeal = time?.let {
            when {
                !it.isBefore(LocalTime.of(5, 0)) && it.isBefore(LocalTime.of(11, 30)) -> "Breakfast"
                !it.isBefore(LocalTime.of(11, 30)) && it.isBefore(LocalTime.of(16, 0)) -> "Lunch"
                !it.isBefore(LocalTime.of(16, 0)) && it.isBefore(LocalTime.of(19, 0)) -> "snacks"
                else -> "Dinner"
            }
        } ?: "Dinner"

        // Match or create subcategory under matchedTop
        val children = allCategories.filter { it.parentId == matchedTop.id }
        val candidateSub = if (isFoodCategory && (prediction.subcategory.isBlank() || prediction.subcategory.equals("General", ignoreCase = true))) {
            timeBasedMeal
        } else {
            prediction.subcategory
        }
        val cleanPredSub = cleanCategoryName(candidateSub)

        val matchedChild = children.firstOrNull {
            cleanCategoryName(it.name) == cleanPredSub
        } ?: children.firstOrNull {
            val clean = cleanCategoryName(it.name)
            clean.contains(cleanPredSub) || cleanPredSub.contains(clean)
        }

        val finalSubId: Long = when {
            matchedChild != null -> matchedChild.id
            candidateSub.isNotBlank() && !candidateSub.equals("General", ignoreCase = true) -> {
                categoryDao.insert(
                    CategoryEntity(
                        name = candidateSub.trim().replaceFirstChar { it.uppercase() },
                        parentId = matchedTop.id,
                        type = TransactionType.EXPENSE,
                    )
                )
            }
            else -> {
                val existingGeneral = children.firstOrNull { it.name.equals("General", ignoreCase = true) }
                existingGeneral?.id ?: categoryDao.insert(
                    CategoryEntity(
                        name = "General",
                        parentId = matchedTop.id,
                        type = TransactionType.EXPENSE,
                    )
                )
            }
        }

        val cleanMerchant = prediction.merchant.ifBlank { fallbackMerchant }
        val rawNote = prediction.note.trim()
        val strippedNote = when {
            cleanMerchant.isNotBlank() && rawNote.startsWith("$cleanMerchant for ", ignoreCase = true) ->
                rawNote.substring("$cleanMerchant for ".length).trim()
            cleanMerchant.isNotBlank() && rawNote.startsWith("$cleanMerchant - ", ignoreCase = true) ->
                rawNote.substring("$cleanMerchant - ".length).trim()
            cleanMerchant.isNotBlank() && rawNote.startsWith("$cleanMerchant: ", ignoreCase = true) ->
                rawNote.substring("$cleanMerchant: ".length).trim()
            cleanMerchant.isNotBlank() && rawNote.equals(cleanMerchant, ignoreCase = true) ->
                ""
            else -> rawNote
        }
        val cleanNote = strippedNote.ifBlank {
            if (candidateSub.isNotBlank() && !candidateSub.equals("General", ignoreCase = true)) {
                candidateSub.trim()
            } else if (isFoodCategory) {
                timeBasedMeal
            } else {
                cleanMerchant
            }
        }

        return CategorizationResult(
            topCategoryId = matchedTop.id,
            subCategoryId = finalSubId,
            finalCategoryId = finalSubId,
            predictedNote = cleanNote,
            predictedMerchant = cleanMerchant,
            categorizationNotes = prediction.categorizationNotes,
            confidence = 1.0f,
        )
    }

    private suspend fun fallbackResult(
        otherCategory: CategoryEntity,
        allCategories: List<CategoryEntity>,
        fallbackMerchant: String,
    ): CategorizationResult {
        val children = allCategories.filter { it.parentId == otherCategory.id }
        val generalSub = children.firstOrNull { it.name.equals("General", ignoreCase = true) }
        val subCatId = generalSub?.id ?: categoryDao.insert(
            CategoryEntity(
                name = "General",
                parentId = otherCategory.id,
                type = TransactionType.EXPENSE,
            )
        )
        return CategorizationResult(
            topCategoryId = otherCategory.id,
            subCategoryId = subCatId,
            finalCategoryId = subCatId,
            predictedNote = "",
            predictedMerchant = fallbackMerchant,
            categorizationNotes = null,
            confidence = 0f,
        )
    }

    suspend fun generateCorrectionNotes(
        merchant: String,
        amount: BigDecimal,
        date: LocalDate,
        oldCategoryName: String,
        oldSubcategoryName: String?,
        oldNotes: String?,
        newCategoryName: String,
        newSubcategoryName: String?,
        userNote: String?,
    ): String? {
        val apiKey = settingsRepository.observe().first().geminiApiKey
        if (apiKey.isNullOrBlank()) {
            return "User manually corrected to $newCategoryName - ${newSubcategoryName ?: "General"}."
        }

        val prompt = """
        A user manually corrected a financial transaction that was previously classified.

        Transaction Details:
        - Merchant: $merchant
        - Amount: ₹${amount.toPlainString()}
        - Date: $date

        Previous AI Classification:
        - Category: $oldCategoryName -> ${oldSubcategoryName ?: "General"}
        - Previous AI Notes: ${oldNotes ?: "None"}

        User Correction (Ground Truth):
        - Selected Category: $newCategoryName -> ${newSubcategoryName ?: "General"}
        - User Note: ${userNote ?: "None"}

        Task:
        Provide a concise, updated AI reasoning note (maximum 15 words) explaining why this transaction belongs to "$newCategoryName -> ${newSubcategoryName ?: "General"}" based on the merchant and user correction, to guide future classifications.

        Output strict JSON ONLY in this format:
        {
          "categorizationNotes": "<concise explanation, max 15 words>"
        }
        """.trimIndent()

        Log.i("CategorizationEngine", "Correction Prompt:\n$prompt")
        try {
            java.io.File("/data/data/com.moneymanager/cache/last_correction_prompt.txt").writeText(prompt)
        } catch (_: Throwable) {}

        val result = cloudGeminiClient.generateContent(
            apiKey = apiKey,
            prompt = prompt,
            systemInstruction = "You are an expert personal finance assistant updating categorization reasoning after user feedback. Output strict JSON only.",
        )

        result.onSuccess {
            Log.i("CategorizationEngine", "Correction Response:\n$it")
        }

        return result.mapCatching { rawJson ->
            val trimmed = rawJson.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            try {
                val json = JSONObject(trimmed)
                val note = json.optString("categorizationNotes").ifBlank {
                    json.optString("categorizedNotes").ifBlank {
                        json.optString("aiNotes")
                    }
                }.trim()
                if (note.isNotBlank()) return@mapCatching note
            } catch (_: Throwable) {}

            Regex(""""(?:categorizationNotes|categorizedNotes|aiNotes)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(trimmed)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "User manually corrected to $newCategoryName - ${newSubcategoryName ?: "General"}."
    }

    fun generateAndSaveCorrectionNotes(
        transactionId: Long,
        merchant: String,
        amount: BigDecimal,
        date: LocalDate,
        originalCategoryId: Long,
        newCategoryId: Long,
        originalNotes: String?,
        userNote: String?,
    ) {
        engineScope.launch {
            try {
                val origCat = categoryDao.getById(originalCategoryId)
                val origTop = origCat?.parentId?.let { categoryDao.getById(it) } ?: origCat
                val origSub = if (origCat?.parentId != null) origCat.name else "General"
                val origTopName = origTop?.name ?: "Other"

                val newCat = categoryDao.getById(newCategoryId)
                val newTop = newCat?.parentId?.let { categoryDao.getById(it) } ?: newCat
                val newSub = if (newCat?.parentId != null) newCat.name else "General"
                val newTopName = newTop?.name ?: "Other"

                val updatedNotes = generateCorrectionNotes(
                    merchant = merchant,
                    amount = amount,
                    date = date,
                    oldCategoryName = origTopName,
                    oldSubcategoryName = origSub,
                    oldNotes = originalNotes,
                    newCategoryName = newTopName,
                    newSubcategoryName = newSub,
                    userNote = userNote,
                )

                if (!updatedNotes.isNullOrBlank()) {
                    transactionDao.updateCategorizationNotes(transactionId, updatedNotes)
                    Log.i("CategorizationEngine", "Updated transaction $transactionId categorizationNotes to: $updatedNotes")
                }
            } catch (e: Throwable) {
                Log.w("CategorizationEngine", "Failed to generate correction notes for transaction $transactionId", e)
            }
        }
    }

    suspend fun train(
        merchant: String,
        note: String?,
        amount: BigDecimal,
        date: LocalDate = LocalDate.now(),
        assignedCategoryId: Long,
    ) {
        // Transactions saved in Room automatically serve as history in future prompts
    }

    suspend fun seedInitialWeights() {}

    suspend fun seedFromTransactions(
        transactions: List<com.moneymanager.data.local.entity.TransactionEntity>,
        categories: List<CategoryEntity> = emptyList(),
    ) {}
}
