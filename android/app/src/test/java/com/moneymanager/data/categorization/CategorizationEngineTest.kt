package com.moneymanager.data.categorization

import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.CategoryMlWeightDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.CategoryMlWeightEntity
import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.data.repository.FakeCategoryDao
import com.moneymanager.data.repository.InMemoryDb
import com.moneymanager.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class FakeCategoryMlWeightDao : CategoryMlWeightDao {
    val weights = mutableListOf<CategoryMlWeightEntity>()
    private var nextId = 1L

    override suspend fun getWeightsForFeature(featureKey: String): List<CategoryMlWeightEntity> =
        weights.filter { it.featureKey == featureKey }

    override suspend fun getWeightsForFeatures(featureKeys: List<String>): List<CategoryMlWeightEntity> =
        weights.filter { it.featureKey in featureKeys }

    override suspend fun getAllWeights(): List<CategoryMlWeightEntity> = weights.toList()

    override suspend fun getCount(): Int = weights.size

    override suspend fun insertAll(weights: List<CategoryMlWeightEntity>) {
        for (w in weights) {
            val index = this.weights.indexOfFirst { it.featureKey == w.featureKey && it.categoryId == w.categoryId }
            if (index >= 0) {
                this.weights[index] = w.copy(id = this.weights[index].id)
            } else {
                this.weights.add(w.copy(id = nextId++))
            }
        }
    }

    override suspend fun incrementWeight(featureKey: String, categoryId: Long, timestamp: Long) {
        val index = weights.indexOfFirst { it.featureKey == featureKey && it.categoryId == categoryId }
        if (index >= 0) {
            val existing = weights[index]
            weights[index] = existing.copy(count = existing.count + 1, lastUpdatedEpochMs = timestamp)
        } else {
            weights.add(CategoryMlWeightEntity(id = nextId++, featureKey = featureKey, categoryId = categoryId, count = 1, lastUpdatedEpochMs = timestamp))
        }
    }

    override suspend fun deleteAll() {
        weights.clear()
    }
}

class CategorizationEngineTest {
    private lateinit var db: InMemoryDb
    private lateinit var categoryDao: CategoryDao
    private lateinit var mlWeightDao: FakeCategoryMlWeightDao
    private lateinit var engine: CategorizationEngine

    private val foodId = 1L
    private val breakfastId = 2L
    private val lunchId = 3L
    private val dinnerId = 4L
    private val travelId = 5L
    private val petrolId = 6L
    private val cabAutoId = 7L
    private val publicTransportId = 8L
    private val otherId = 9L

    @Before
    fun setUp() {
        db = InMemoryDb()
        categoryDao = FakeCategoryDao(db)
        mlWeightDao = FakeCategoryMlWeightDao()
        engine = CategorizationEngine(categoryDao, mlWeightDao)

        // Seed Categories with emojis and normalized Travel subcategories
        db.categories.value = listOf(
            CategoryEntity(id = foodId, name = "🍜 Food", parentId = null, type = TransactionType.EXPENSE),
            CategoryEntity(id = breakfastId, name = "Breakfast", parentId = foodId, type = TransactionType.EXPENSE),
            CategoryEntity(id = lunchId, name = "Lunch", parentId = foodId, type = TransactionType.EXPENSE),
            CategoryEntity(id = dinnerId, name = "Dinner", parentId = foodId, type = TransactionType.EXPENSE),
            CategoryEntity(id = travelId, name = "🚖 Travel", parentId = null, type = TransactionType.EXPENSE),
            CategoryEntity(id = petrolId, name = "petrol", parentId = travelId, type = TransactionType.EXPENSE),
            CategoryEntity(id = cabAutoId, name = "Cab / Auto", parentId = travelId, type = TransactionType.EXPENSE),
            CategoryEntity(id = publicTransportId, name = "Public Transport", parentId = travelId, type = TransactionType.EXPENSE),
            CategoryEntity(id = otherId, name = "Others", parentId = null, type = TransactionType.EXPENSE),
        )
    }

    @Test
    fun `falls back to Other when no matching signals exist`() = runTest {
        val result = engine.predictCategory(
            merchant = "UnknownVendorX",
            note = null,
            amount = BigDecimal("150"),
        )
        assertEquals(otherId, result.topCategoryId)
        assertNull(result.subCategoryId)
        assertEquals(otherId, result.finalCategoryId)
    }

    @Test
    fun `Priority 1 - GPay split note with generic item Food - for-eggs lands in top-level Food only`() = runTest {
        val result = engine.predictCategory(
            merchant = "Google Pay",
            note = "Food - for-eggs",
            amount = BigDecimal("50"),
            time = LocalTime.of(8, 30),
        )
        // Must land in top-level Food ONLY, NOT in Breakfast!
        assertEquals(foodId, result.topCategoryId)
        assertNull(result.subCategoryId)
        assertEquals(foodId, result.finalCategoryId)
    }

    @Test
    fun `Priority 1 - GPay split note with explicit meal Food - dinner lands in Food and Dinner`() = runTest {
        val result = engine.predictCategory(
            merchant = "Google Pay",
            note = "Food - dinner",
            amount = BigDecimal("200"),
        )
        assertEquals(foodId, result.topCategoryId)
        assertEquals(dinnerId, result.subCategoryId)
        assertEquals(dinnerId, result.finalCategoryId)
    }

    @Test
    fun `Priority 1 - GPay split note with Food - breakfast lands in Food and Breakfast`() = runTest {
        val result = engine.predictCategory(
            merchant = "Google Pay",
            note = "Food - breakfast",
            amount = BigDecimal("80"),
        )
        assertEquals(foodId, result.topCategoryId)
        assertEquals(breakfastId, result.subCategoryId)
        assertEquals(breakfastId, result.finalCategoryId)
    }

    @Test
    fun `Food items like idly, dosa, or biryani land in top-level Food only without subcategory`() = runTest {
        val result = engine.predictCategory(
            merchant = "Hotel Saravana",
            note = "idly and dosa",
            amount = BigDecimal("90"),
        )
        assertEquals(foodId, result.topCategoryId)
        assertNull(result.subCategoryId)
        assertEquals(foodId, result.finalCategoryId)
    }

    @Test
    fun `Cab, auto, and rapido all combine into Cab Auto subcategory`() = runTest {
        val rapidoResult = engine.predictCategory(merchant = "Rapido", note = null, amount = BigDecimal("60"))
        assertEquals(travelId, rapidoResult.topCategoryId)
        assertEquals(cabAutoId, rapidoResult.subCategoryId)

        val uberResult = engine.predictCategory(merchant = "Uber", note = "cab ride", amount = BigDecimal("300"))
        assertEquals(travelId, uberResult.topCategoryId)
        assertEquals(cabAutoId, uberResult.subCategoryId)

        val autoResult = engine.predictCategory(merchant = "Auto driver", note = "auto", amount = BigDecimal("100"))
        assertEquals(travelId, autoResult.topCategoryId)
        assertEquals(cabAutoId, autoResult.subCategoryId)
    }

    @Test
    fun `Bus, redbus, and train map to Public Transport subcategory`() = runTest {
        val busResult = engine.predictCategory(merchant = "KSRTC", note = "bus ticket", amount = BigDecimal("150"))
        assertEquals(travelId, busResult.topCategoryId)
        assertEquals(publicTransportId, busResult.subCategoryId)

        val trainResult = engine.predictCategory(merchant = "IRCTC", note = "train", amount = BigDecimal("250"))
        assertEquals(travelId, trainResult.topCategoryId)
        assertEquals(publicTransportId, trainResult.subCategoryId)
    }

    @Test
    fun `Priority 2 - Learned transaction history categorizes friend transfer`() = runTest {
        val friendMerchant = "A/c 987654321012"

        // Unknown initially
        val initial = engine.predictCategory(merchant = friendMerchant, note = null, amount = BigDecimal("200"))
        assertEquals(otherId, initial.topCategoryId)

        // User edits/assigns Food
        engine.train(
            merchant = friendMerchant,
            note = null,
            amount = BigDecimal("200"),
            assignedCategoryId = foodId,
        )

        // Next time: learns Food from history
        val learned = engine.predictCategory(merchant = friendMerchant, note = null, amount = BigDecimal("250"))
        assertEquals(foodId, learned.topCategoryId)
    }

    @Test
    fun `Priority 3 - Seeded keyword Swiggy categorizes to Food`() = runTest {
        val result = engine.predictCategory(
            merchant = "Swiggy",
            note = null,
            amount = BigDecimal("350"),
        )
        assertEquals(foodId, result.topCategoryId)
    }

    @Test
    fun `seedInitialWeights populates weights`() = runTest {
        assertEquals(0, mlWeightDao.weights.size)
        engine.seedInitialWeights()
        assert(mlWeightDao.weights.isNotEmpty())
    }

    @Test
    fun `seedFromTransactions accurately populates weights`() = runTest {
        val historical = listOf(
            TransactionEntity(
                id = 1,
                type = TransactionType.EXPENSE,
                date = LocalDate.now(),
                amount = BigDecimal("150"),
                categoryId = lunchId,
                accountId = 1,
                note = "meals",
                merchant = "Bawarchi",
            ),
        )

        engine.seedFromTransactions(historical, db.categories.value)
        val weights = mlWeightDao.getAllWeights()
        assert(weights.any { it.featureKey == "merchant:bawarchi" && it.categoryId == lunchId })
        assert(weights.any { it.featureKey == "merchant:bawarchi" && it.categoryId == foodId })
    }
}
