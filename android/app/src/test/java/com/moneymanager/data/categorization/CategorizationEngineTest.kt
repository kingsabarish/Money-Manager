package com.moneymanager.data.categorization

import com.moneymanager.data.ingestion.TransactionSource
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.repository.FakeCategoryDao
import com.moneymanager.data.repository.FakeTransactionDao
import com.moneymanager.data.repository.InMemoryDb
import com.moneymanager.domain.model.AppSettings
import com.moneymanager.domain.model.BackupFrequency
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.domain.model.TransactionType
import com.moneymanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FakeCloudGeminiClient(
    var responses: MutableList<Result<String>> = mutableListOf(),
) : CloudGeminiClient {
    val prompts = mutableListOf<String>()
    var lastApiKey: String? = null

    override suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstruction: String?,
    ): Result<String> {
        lastApiKey = apiKey
        prompts.add(prompt)
        return if (responses.isNotEmpty()) responses.removeAt(0) else Result.failure(RuntimeException("No response"))
    }
}

class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(geminiApiKey = "test-api-key"),
) : SettingsRepository {
    private val flow = MutableStateFlow(initialSettings)

    override fun observe(): Flow<AppSettings> = flow
    override suspend fun setThemeMode(mode: ThemeMode) { flow.value = flow.value.copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) { flow.value = flow.value.copy(dynamicColor = enabled) }
    override suspend fun setSeedColor(argb: Int) { flow.value = flow.value.copy(seedColorArgb = argb) }
    override suspend fun setLastBackupAt(epochMs: Long) { flow.value = flow.value.copy(lastBackupAtEpochMs = epochMs) }
    override suspend fun clearLastBackupAt() { flow.value = flow.value.copy(lastBackupAtEpochMs = null) }
    override suspend fun setBackupFrequency(frequency: BackupFrequency) { flow.value = flow.value.copy(backupFrequency = frequency) }
    override suspend fun setGeminiApiKey(apiKey: String?) { flow.value = flow.value.copy(geminiApiKey = apiKey) }
}

class CategorizationEngineTest {
    private lateinit var db: InMemoryDb
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var fakeGeminiClient: FakeCloudGeminiClient
    private lateinit var fakeSettingsRepo: FakeSettingsRepository
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
        fakeGeminiClient = FakeCloudGeminiClient()
        fakeSettingsRepo = FakeSettingsRepository()
        engine = CategorizationEngine(
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            settingsRepository = fakeSettingsRepo,
            cloudGeminiClient = fakeGeminiClient,
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
    fun `when Gemini API key is missing, falls back to Other with General subcategory and empty note`() = runTest {
        fakeSettingsRepo.setGeminiApiKey(null)

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
    fun `predicts category, merchant, note, and categorizationNotes using Cloud Gemini in single call`() = runTest {
        // Pre-populate some historical transactions
        db.transactions.value = listOf(
            com.moneymanager.data.local.entity.TransactionEntity(
                id = 101L,
                type = TransactionType.EXPENSE,
                date = LocalDate.now().minusDays(20),
                amount = BigDecimal("350.00"),
                categoryId = dinnerId,
                accountId = 1L,
                note = "Dinner",
                isApproved = true,
                merchant = "Swiggy",
                categorizationNotes = "Late night order via Swiggy",
            )
        )

        fakeGeminiClient.responses = mutableListOf(
            Result.success(
                """
                {
                  "merchant": "Swiggy",
                  "category": "Food",
                  "subcategory": "Dinner",
                  "note": "Dinner",
                  "categorizationNotes": "Food delivery app order matching past dinner transactions"
                }
                """.trimIndent()
            )
        )

        val result = engine.predictCategory(
            rawMessage = "Rs 450.00 debited from A/c XX4006 at SWIGGY for Dinner",
            source = TransactionSource.SMS,
            amount = BigDecimal("450.00"),
            fallbackMerchant = "SWIGGY for Dinner",
            fallbackNote = null,
        )

        assertEquals(foodId, result.topCategoryId)
        assertEquals(dinnerId, result.subCategoryId)
        assertEquals(dinnerId, result.finalCategoryId)
        assertEquals("Swiggy", result.predictedMerchant)
        assertEquals("Dinner", result.predictedNote)
        assertEquals("Food delivery app order matching past dinner transactions", result.categorizationNotes)
        assertEquals(1.0f, result.confidence, 0.001f)

        // Verify prompt included full 1.5 years TSV history and word limit instructions
        val prompt = fakeGeminiClient.prompts.first()
        assertTrue(prompt.contains("Past 1.5 Years Transaction History (TSV format):"))
        assertTrue(prompt.contains("Late night order via Swiggy"))
        assertTrue(prompt.contains("maximum 5 words!"))
        assertTrue(prompt.contains("maximum 15 words!"))
    }

    @Test
    fun `auto-creates new subcategory when Cloud Gemini suggests an unmapped subcategory`() = runTest {
        fakeGeminiClient.responses = mutableListOf(
            Result.success(
                """
                {
                  "merchant": "Metro",
                  "category": "Travel",
                  "subcategory": "Metro",
                  "note": "Metro train ticket",
                  "categorizationNotes": "Subway transit commute"
                }
                """.trimIndent()
            )
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
        assertEquals("Subway transit commute", result.categorizationNotes)

        // Ensure "Metro" subcategory was created in DB under Travel
        val createdSub = db.categories.value.firstOrNull { it.id == result.subCategoryId }
        assertNotNull(createdSub)
        assertEquals("Metro", createdSub?.name)
        assertEquals(travelId, createdSub?.parentId)
    }

    @Test
    fun `gracefully falls back to Other and empty note when Cloud Gemini returns an error`() = runTest {
        fakeGeminiClient.responses = mutableListOf(
            Result.failure(RuntimeException("Network error / Timeout"))
        )

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
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `handles categorizedNotes and aiNotes aliases in Gemini JSON response`() = runTest {
        fakeGeminiClient.responses = mutableListOf(
            Result.success(
                """
                {
                  "merchant": "Swiggy",
                  "category": "Food",
                  "subcategory": "Dinner",
                  "note": "Dinner",
                  "categorizedNotes": "Matched to dinner based on evening timing."
                }
                """.trimIndent()
            )
        )

        val result = engine.predictCategory(
            rawMessage = "Rs 350 debited at Swiggy",
            source = TransactionSource.SMS,
            amount = BigDecimal("350.00"),
            fallbackMerchant = "Swiggy",
            time = java.time.LocalTime.of(20, 0),
        )

        assertEquals(foodId, result.topCategoryId)
        assertEquals(dinnerId, result.subCategoryId)
        assertEquals("Matched to dinner based on evening timing.", result.categorizationNotes)
    }

    @Test
    fun `generateCorrectionNotes queries Gemini and returns updated notes`() = runTest {
        fakeGeminiClient.responses = mutableListOf(
            Result.success(
                """
                {
                  "categorizationNotes": "User corrected to Lunch based on midday meal."
                }
                """.trimIndent()
            )
        )

        val note = engine.generateCorrectionNotes(
            merchant = "Paytm",
            amount = BigDecimal("440.00"),
            date = LocalDate.now(),
            oldCategoryName = "Food",
            oldSubcategoryName = "Breakfast",
            oldNotes = "Matched to breakfast",
            newCategoryName = "Food",
            newSubcategoryName = "Lunch",
            userNote = "Lunch",
        )

        assertEquals("User corrected to Lunch based on midday meal.", note)
    }
}
