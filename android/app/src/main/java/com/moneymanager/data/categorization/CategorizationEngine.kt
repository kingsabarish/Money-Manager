package com.moneymanager.data.categorization

import android.util.Log
import com.moneymanager.data.ingestion.TransactionSource
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.TransactionType
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
    val confidence: Float = 1.0f,
)

@Singleton
class CategorizationEngine @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val queryExecutor: TransactionHistoryQueryExecutor,
    private val aiCoreManager: AiCoreManager,
) {
    /**
     * Cleans a category name by removing emojis, punctuation, and non-alphanumerics.
     */
    fun cleanCategoryName(name: String): String =
        name.lowercase().replace(Regex("""[^a-z0-9\s/]"""), "").trim()

    /**
     * Checks if AICore on-device Gemini Nano is available on this device.
     */
    suspend fun isAiCoreSupported(): Boolean = aiCoreManager.isAiCoreSupported()

    /**
     * Predicts the category, subcategory, note, and merchant using an on-device ReAct tool-calling agent flow.
     * Turn 1: Gemini Nano generates a read-only SQLite SELECT query to retrieve relevant historical transactions.
     * Tool: The app executes the query against `v_transaction_history` with strict read-only/LIMIT safety constraints.
     * Turn 2: Gemini Nano receives the query output and classifies the transaction into Category/Subcategory/Note/Merchant.
     *
     * On unsupported devices or if inference fails, falls back to "Other" -> "General" with an empty note.
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
                confidence = 0f,
            )
        }

        val topCategories = allCategories.filter { it.parentId == null }
        val otherCategory = topCategories.firstOrNull {
            val clean = cleanCategoryName(it.name)
            clean == "other" || clean == "others"
        } ?: topCategories.first()

        // 1. Verify AICore availability
        val isSupported = aiCoreManager.isAiCoreSupported()
        if (!isSupported) {
            Log.i("CategorizationEngine", "AICore not available on this device; defaulting to Other -> General with empty note")
            return fallbackResult(otherCategory, allCategories, fallbackMerchant)
        }

        // 2. ReAct Agent Flow
        try {
            // Turn 1: Ask Gemini Nano to write a read-only SQLite SELECT query
            val queryPrompt = buildQueryGenerationPrompt(rawMessage, source, amount)
            Log.d("CategorizationEngine", "Sending Turn 1 Query Prompt to Gemini Nano:\n$queryPrompt")
            val rawQueryResponse = aiCoreManager.generateContent(queryPrompt)
            Log.i("CategorizationEngine", "Gemini Nano Turn 1 Query Response:\n$rawQueryResponse")

            val parsedQuery = parseQueryResponse(rawQueryResponse)
            Log.d("CategorizationEngine", "Parsed query from agent: $parsedQuery")

            // Tool Execution: Query SQLite history (with 75 limit and fallback)
            val historyResult = queryExecutor.executeQuery(parsedQuery, limit = 75)
            Log.d("CategorizationEngine", "Tool output: returned ${historyResult.records.size} rows (wasFallback=${historyResult.wasFallback})")

            // Turn 2: Feed tool output to Gemini Nano for final classification
            val classifyPrompt = buildClassificationPrompt(
                rawMessage = rawMessage,
                source = source,
                amount = amount,
                allCategories = allCategories,
                historyResult = historyResult,
                time = time,
            )
            Log.d("CategorizationEngine", "Sending Turn 2 Classification Prompt to Gemini Nano:\n$classifyPrompt")
            val rawClassifyResponse = aiCoreManager.generateContent(classifyPrompt)
            Log.i("CategorizationEngine", "Gemini Nano Turn 2 Response:\n$rawClassifyResponse")

            val prediction = parseClassificationResponse(rawClassifyResponse)
            if (prediction != null) {
                return resolvePrediction(prediction, allCategories, fallbackMerchant, otherCategory, time)
            }
        } catch (e: Throwable) {
            Log.e("CategorizationEngine", "Agentic categorization failed, falling back to Other -> General", e)
        }

        return fallbackResult(otherCategory, allCategories, fallbackMerchant)
    }

    private fun buildQueryGenerationPrompt(
        rawMessage: String,
        source: TransactionSource,
        amount: BigDecimal,
    ): String {
        val sourceDesc = when (source) {
            TransactionSource.SMS -> "Bank SMS"
            TransactionSource.GPAY_SPLIT -> "Google Pay"
        }

        return """
        You are an on-device personal finance agent for Money Manager.
        Your task is to analyze an incoming expense message and write a read-only SQLite SELECT query to retrieve relevant historical transactions to help categorize it.

        Sources Guide:
        1. Bank SMS: Typically contains patterns like "debited at <Merchant> for <Purpose>", "transferred to <UPI_VPA>", or "used at <Store>". Identify potential vendor names or keywords.
        2. Google Pay: Typically formatted as "<Group/Merchant> - <Description>" (e.g., "Food - for-eggs", "Tea Stall - snacks") or "Paid ₹... to <Merchant>".

        Incoming Transaction:
        - Source: $sourceDesc
        - Message: "$rawMessage"
        - Amount: ₹${amount.toPlainString()}

        Available Read-Only Database View:
        v_transaction_history (
            date TEXT,           -- ISO date 'YYYY-MM-DD'
            amount TEXT,         -- Decimal amount in INR
            merchant TEXT,       -- Clean merchant/payee name
            note TEXT,           -- Transaction description
            category TEXT,       -- Top-level category
            subcategory TEXT     -- Subcategory name
        )

        Sample Records:
        - 2026-10-25 | ₹320.00 | Swiggy | Dinner | 🍜 Food | Dinner
        - 2026-10-20 | ₹210.00 | Uber | Cab to office | 🚖 Travel | Cab / Auto

        Instructions:
        Write a single SQLite SELECT query to find past transactions matching the merchant, keywords, or similar expenses.
        - Must ONLY be a SELECT query.
        - Use columns: merchant, amount, category, subcategory, note.
        - In WHERE clause, search by merchant or note keywords (e.g. WHERE merchant LIKE '%swiggy%' OR note LIKE '%dinner%').
        - Do NOT filter by amount or include currency symbols (amount in the view is a plain decimal string, and past orders for the same merchant typically have different amounts).
        - Max limit is 75 rows.

        Output strict JSON:
        {"query": "SELECT merchant, amount, category, subcategory, note FROM v_transaction_history WHERE ... LIMIT 75"}
        """.trimIndent()
    }

    private fun parseQueryResponse(rawResponse: String): String? {
        val trimmed = rawResponse.trim()
        val match = Regex(""""query"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
        if (match != null) {
            return match.groupValues[1].replace("\\\"", "\"").trim()
        }
        if (trimmed.startsWith("SELECT", ignoreCase = true)) {
            return trimmed
        }
        return null
    }

    private fun buildClassificationPrompt(
        rawMessage: String,
        source: TransactionSource,
        amount: BigDecimal,
        allCategories: List<CategoryEntity>,
        historyResult: HistoryQueryResult,
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

        val historyHeader = if (historyResult.wasFallback) {
            "Retrieved Historical Records (Tool Output - Note: No matching historical records found for your search query. The following 75 records are the user's most recent general transactions for reference):"
        } else {
            "Retrieved Historical Records (Tool Output - Matches found):"
        }

        val formattedRecords = if (historyResult.records.isNotEmpty()) {
            historyResult.records.take(75).joinToString("\n") { r ->
                val catDisplay = if (r.subcategory.isNotBlank() && !r.subcategory.equals("General", ignoreCase = true)) {
                    "${r.category} > ${r.subcategory}"
                } else {
                    r.category
                }
                val noteDisplay = if (r.note.isNotBlank()) ": ${r.note}" else ""
                "- \"${r.merchant.ifBlank { "Unknown" }}\" (₹${r.amount.toPlainString()}) -> $catDisplay$noteDisplay"
            }
        } else {
            "- No prior records available."
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
        You are an on-device personal finance assistant for Money Manager.
        Classify this expense transaction into the best Category and Subcategory, extract the clean Merchant name, and write a concise, clean Note.

        Transaction to Classify:
        - Source: $sourceDesc
        - Message: "$rawMessage"
        - Amount: ₹${amount.toPlainString()}
        - Time of Day: $timeDesc

        Allowed Categories and Subcategories:
        $categoryTree

        $historyHeader
        $formattedRecords

        Requirements:
        1. "merchant": The clean, recognizable payee/merchant name (e.g. "Swiggy", "Uber", "Amazon").
        2. "category": Must be one of the top-level categories listed above.
        3. "subcategory": Must be a relevant subcategory for that category.
           - For "🍜 Food", select the meal subcategory (Breakfast, Lunch, snacks, Dinner) based on the Time of Day above and the user's past historical patterns. For example, if transaction was in the morning, use "Breakfast"; if midday, use "Lunch"; if late afternoon, use "snacks"; if evening/night, use "Dinner".
        4. "note": Follow the user's historical naming style closely! For example, if past notes for this merchant or meal type are simple keywords (e.g. "Dinner", "Lunch", "Breakfast", "Snacks"), use just that keyword ("Dinner") and do NOT include the merchant name or conjunctions (never write "Swiggy for Dinner" or "Swiggy Dinner"). Do NOT include card numbers, bank reference IDs, or OTPs.

        Output strict JSON ONLY:
        {"merchant": "<Merchant>", "category": "<Category>", "subcategory": "<Subcategory>", "note": "<Note>"}
        """.trimIndent()
    }

    private data class PredictionJson(
        val merchant: String,
        val category: String,
        val subcategory: String,
        val note: String,
    )

    private fun parseClassificationResponse(rawResponse: String): PredictionJson? {
        val trimmed = rawResponse.trim()
        val merchant = Regex(""""merchant"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
        val cat = Regex(""""category"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
        val sub = Regex(""""subcategory"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
        val note = Regex(""""note"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()

        if (cat.isNullOrBlank()) return null
        return PredictionJson(
            merchant = merchant.orEmpty(),
            category = cat,
            subcategory = sub.orEmpty(),
            note = note.orEmpty(),
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
            confidence = 0f,
        )
    }

    /**
     * In Gemini Nano on-device architecture, all saved and edited transactions
     * in Room serve directly as training examples for subsequent inferences.
     */
    suspend fun train(
        merchant: String,
        note: String?,
        amount: BigDecimal,
        date: LocalDate = LocalDate.now(),
        assignedCategoryId: Long,
    ) {
        Log.d("CategorizationEngine", "Payee '$merchant' learned under category $assignedCategoryId for future prompts.")
    }

    suspend fun seedInitialWeights() {
        // No-op in Gemini Nano on-device architecture
    }

    suspend fun seedFromTransactions(
        transactions: List<com.moneymanager.data.local.entity.TransactionEntity>,
        categories: List<CategoryEntity> = emptyList(),
    ) {
        // No-op in Gemini Nano on-device architecture
    }
}
