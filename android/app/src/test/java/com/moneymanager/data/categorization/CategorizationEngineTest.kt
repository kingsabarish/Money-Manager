package com.moneymanager.data.categorization

import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.CategoryMlWeightDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.CategoryMlWeightEntity
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
    private val shoppingId = 3L
    private val groceriesId = 4L
    private val otherId = 5L

    @Before
    fun setUp() {
        db = InMemoryDb()
        categoryDao = FakeCategoryDao(db)
        mlWeightDao = FakeCategoryMlWeightDao()
        engine = CategorizationEngine(categoryDao, mlWeightDao)

        // Seed Categories
        db.categories.value = listOf(
            CategoryEntity(id = foodId, name = "Food", parentId = null, type = TransactionType.EXPENSE),
            CategoryEntity(id = breakfastId, name = "Breakfast", parentId = foodId, type = TransactionType.EXPENSE),
            CategoryEntity(id = shoppingId, name = "Shopping", parentId = null, type = TransactionType.EXPENSE),
            CategoryEntity(id = groceriesId, name = "Groceries", parentId = null, type = TransactionType.EXPENSE),
            CategoryEntity(id = otherId, name = "Other", parentId = null, type = TransactionType.EXPENSE),
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
    fun `learns merchant mapping after training`() = runTest {
        // Initially unknown
        val initial = engine.predictCategory(merchant = "Chai Point", note = null, amount = BigDecimal("100"))
        assertEquals(otherId, initial.topCategoryId)

        // Train: user approves Chai Point as Food
        engine.train(
            merchant = "Chai Point",
            note = null,
            amount = BigDecimal("100"),
            assignedCategoryId = foodId,
        )

        // Predict again: now predicts Food
        val predicted = engine.predictCategory(merchant = "Chai Point", note = null, amount = BigDecimal("100"))
        assertEquals(foodId, predicted.topCategoryId)
    }

    @Test
    fun `weighted scoring prioritizes dominant category over one-off anomaly`() = runTest {
        // Train Amazon 4 times as Shopping
        repeat(4) {
            engine.train(merchant = "Amazon", note = null, amount = BigDecimal("1500"), assignedCategoryId = shoppingId)
        }
        // One-off anomaly: Train Amazon 1 time as Groceries
        engine.train(merchant = "Amazon", note = null, amount = BigDecimal("500"), assignedCategoryId = groceriesId)

        // Predict for Amazon: Shopping should still win comfortably
        val predicted = engine.predictCategory(merchant = "Amazon", note = null, amount = BigDecimal("800"))
        assertEquals(shoppingId, predicted.topCategoryId)
    }

    @Test
    fun `predicts subcategory when confident signal is present`() = runTest {
        // Train morning coffee at Starbucks as Breakfast under Food
        val morning = LocalTime.of(8, 30)
        engine.train(
            merchant = "Starbucks",
            note = "croissant and coffee",
            amount = BigDecimal("350"),
            time = morning,
            assignedCategoryId = breakfastId,
        )

        val predicted = engine.predictCategory(
            merchant = "Starbucks",
            note = "coffee",
            amount = BigDecimal("250"),
            time = morning,
        )

        assertEquals(foodId, predicted.topCategoryId)
        assertEquals(breakfastId, predicted.subCategoryId)
        assertEquals(breakfastId, predicted.finalCategoryId)
    }
}

