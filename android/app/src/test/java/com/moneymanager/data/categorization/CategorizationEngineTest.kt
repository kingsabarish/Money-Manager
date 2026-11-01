package com.moneymanager.data.categorization

import com.moneymanager.data.ingestion.TransactionParser
import com.moneymanager.data.ingestion.TransactionSource
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.repository.FakeCategoryDao
import com.moneymanager.data.repository.FakeTransactionDao
import com.moneymanager.data.repository.InMemoryDb
import com.moneymanager.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class FakeAiCoreManager(
    var isSupported: Boolean = false,
    var responses: MutableList<String> = mutableListOf(),
) : AiCoreManager {
    override suspend fun isAiCoreSupported(): Boolean = isSupported

    val prompts = mutableListOf<String>()

    override suspend fun generateContent(prompt: String): String {
        prompts.add(prompt)
        return if (responses.isNotEmpty()) responses.removeAt(0) else ""
    }
}

class FakeTransactionHistoryQueryExecutor(
    var result: HistoryQueryResult = HistoryQueryResult(records = emptyList(), wasFallback = true),
) : TransactionHistoryQueryExecutor {
    var lastExecutedQuery: String? = null

    override suspend fun executeQuery(sql: String?, limit: Int): HistoryQueryResult {
        lastExecutedQuery = sql
        return result
    }
}

class CategorizationEngineTest {
    private lateinit var db: InMemoryDb
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var fakeQueryExecutor: FakeTransactionHistoryQueryExecutor
    private lateinit var fakeAiCoreManager: FakeAiCoreManager
    private lateinit var engine: CategorizationEngine

    private val foodId = 1L
    private val breakfastId = 2L
    private val lunchId = 3L
    private val dinnerId = 4L
    private val travelId = 5L
    private val petrolId = 6L
    private val cabAutoId = 7L
    private val otherId = 8L

    @Before
    fun setUp() {
        db = InMemoryDb()
        categoryDao = FakeCategoryDao(db)
        transactionDao = FakeTransactionDao(db)
        fakeQueryExecutor = FakeTransactionHistoryQueryExecutor()
        fakeAiCoreManager = FakeAiCoreManager()
        engine = CategorizationEngine(
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            queryExecutor = fakeQueryExecutor,
            aiCoreManager = fakeAiCoreManager,
        )

        db.categories.value = listOf(
            CategoryEntity(id = foodId, name = "Food", parentId = null, type = TransactionType.EXPENSE),
            CategoryEntity(id = breakfastId, name = "Breakfast", parentId = foodId, type = TransactionType.EXPENSE),
            CategoryEntity(id = lunchId, name = "Lunch", parentId = foodId, type = TransactionType.EXPENSE),
            CategoryEntity(id = dinnerId, name = "Dinner", parentId = foodId, type = TransactionType.EXPENSE),
            CategoryEntity(id = travelId, name = "Travel", parentId = null, type = TransactionType.EXPENSE),
            CategoryEntity(id = petrolId, name = "Petrol", parentId = travelId, type = TransactionType.EXPENSE),
            CategoryEntity(id = cabAutoId, name = "Cab / Auto", parentId = travelId, type = TransactionType.EXPENSE),
            CategoryEntity(id = otherId, name = "Other", parentId = null, type = TransactionType.EXPENSE),
        )
    }

    @Test
    fun `when AICore is unsupported, falls back to Other with General subcategory and empty note`() = runTest {
        fakeAiCoreManager.isSupported = false

        val result = engine.predictCategory(
            rawMessage = "Rs 150.00 debited from A/c XX1234 at UnknownStore",
            source = TransactionSource.SMS,
            amount = BigDecimal("150.00"),
            fallbackMerchant = "UnknownStore",
            fallbackNote = "Random Purchase",
        )

        assertEquals(otherId, result.topCategoryId)
        assertNotNull(result.subCategoryId)
        assertEquals(result.subCategoryId, result.finalCategoryId)
        assertEquals("", result.predictedNote)
        assertEquals("UnknownStore", result.predictedMerchant)
        assertEquals(0f, result.confidence, 0.001f)

        // General subcategory was auto-created under Other
        val createdSub = db.categories.value.firstOrNull { it.id == result.subCategoryId }
        assertNotNull(createdSub)
        assertEquals("General", createdSub?.name)
        assertEquals(otherId, createdSub?.parentId)
    }

    @Test
    fun `executes Turn 1 to get SQL query, executes tool, and passes result to Turn 2 for classification`() = runTest {
        fakeAiCoreManager.isSupported = true
        fakeAiCoreManager.responses = mutableListOf(
            // Turn 1 Query Response
            """{"query": "SELECT merchant, amount, category, subcategory, note FROM v_transaction_history WHERE merchant LIKE '%swiggy%' LIMIT 75"}""",
            // Turn 2 Classification Response
            """{"merchant": "Swiggy", "category": "Food", "subcategory": "Dinner", "note": "Dinner"}"""
        )

        fakeQueryExecutor.result = HistoryQueryResult(
            records = listOf(
                TransactionHistoryRecord(
                    date = "2026-10-25",
                    amount = BigDecimal("350.00"),
                    merchant = "Swiggy",
                    note = "Dinner",
                    category = "Food",
                    subcategory = "Dinner",
                )
            ),
            wasFallback = false,
        )

        val result = engine.predictCategory(
            rawMessage = "Rs 450.00 debited from A/c XX4006 at SWIGGY for Dinner",
            source = TransactionSource.SMS,
            amount = BigDecimal("450.00"),
            fallbackMerchant = "SWIGGY for Dinner",
            fallbackNote = null,
        )

        assertEquals("SELECT merchant, amount, category, subcategory, note FROM v_transaction_history WHERE merchant LIKE '%swiggy%' LIMIT 75", fakeQueryExecutor.lastExecutedQuery)
        assertEquals(foodId, result.topCategoryId)
        assertEquals(dinnerId, result.subCategoryId)
        assertEquals(dinnerId, result.finalCategoryId)
        assertEquals("Swiggy", result.predictedMerchant)
        assertEquals("Dinner", result.predictedNote)

        // Verify prompt 2 contains the tool output matches found
        assertTrue(fakeAiCoreManager.prompts[1].contains("Retrieved Historical Records (Tool Output - Matches found):"))
        assertTrue(fakeAiCoreManager.prompts[1].contains(""""Swiggy" (₹350.00) -> Food > Dinner: Dinner"""))
    }

    @Test
    fun `auto-creates new subcategory when Gemini Nano suggests an unmapped subcategory`() = runTest {
        fakeAiCoreManager.isSupported = true
        fakeAiCoreManager.responses = mutableListOf(
            """{"query": "SELECT * FROM v_transaction_history WHERE merchant LIKE '%metro%' LIMIT 75"}""",
            """{"merchant": "Metro", "category": "Travel", "subcategory": "Metro", "note": "Metro train ticket"}"""
        )

        val result = engine.predictCategory(
            rawMessage = "Rs 30.00 spent on BMRC METRO",
            source = TransactionSource.SMS,
            amount = BigDecimal("30.00"),
            fallbackMerchant = "BMRC METRO",
            fallbackNote = "Ticket purchase",
        )

        assertEquals(travelId, result.topCategoryId)
        assertNotNull(result.subCategoryId)
        assertEquals(result.subCategoryId, result.finalCategoryId)
        assertEquals("Metro train ticket", result.predictedNote)
        assertEquals("Metro", result.predictedMerchant)

        // Ensure "Metro" subcategory was created in DB under Travel
        val createdSub = db.categories.value.firstOrNull { it.id == result.subCategoryId }
        assertNotNull(createdSub)
        assertEquals("Metro", createdSub?.name)
        assertEquals(travelId, createdSub?.parentId)
    }

    @Test
    fun `handles query tool fallback and explicitly informs Gemini when no matching query records exist`() = runTest {
        fakeAiCoreManager.isSupported = true
        fakeAiCoreManager.responses = mutableListOf(
            """{"query": "SELECT * FROM v_transaction_history WHERE merchant LIKE '%nonexistent%'"}""",
            """{"merchant": "NewStore", "category": "Other", "subcategory": "General", "note": "New store purchase"}"""
        )

        fakeQueryExecutor.result = HistoryQueryResult(
            records = listOf(
                TransactionHistoryRecord(
                    date = "2026-10-30",
                    amount = BigDecimal("1000.00"),
                    merchant = "RecentStore",
                    note = "General items",
                    category = "Other",
                    subcategory = "General",
                )
            ),
            wasFallback = true,
        )

        val result = engine.predictCategory(
            rawMessage = "Rs 500.00 debited at NewStore",
            source = TransactionSource.SMS,
            amount = BigDecimal("500.00"),
            fallbackMerchant = "NewStore",
        )

        assertEquals(otherId, result.topCategoryId)
        assertEquals("NewStore", result.predictedMerchant)

        // Verify Turn 2 prompt notified Gemini about fallback records
        val classifyPrompt = fakeAiCoreManager.prompts[1]
        assertTrue(classifyPrompt.contains("No matching historical records found for your search query. The following 75 records are the user's most recent general transactions for reference"))
    }

    @Test
    fun `gracefully falls back to Other and empty note when Gemini Nano throws an unexpected error`() = runTest {
        fakeAiCoreManager.isSupported = true
        fakeAiCoreManager.responses = mutableListOf("INVALID_NON_JSON")

        val result = engine.predictCategory(
            rawMessage = "Rs 500.00 debited at Shell",
            source = TransactionSource.SMS,
            amount = BigDecimal("500.00"),
            fallbackMerchant = "Shell",
            fallbackNote = "Fuel purchase",
        )

        assertEquals(otherId, result.topCategoryId)
        assertNotNull(result.subCategoryId)
        assertEquals("", result.predictedNote)
        assertEquals("Shell", result.predictedMerchant)
    }
}
