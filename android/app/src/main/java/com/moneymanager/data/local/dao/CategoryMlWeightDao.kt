package com.moneymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.moneymanager.data.local.entity.CategoryMlWeightEntity

@Dao
interface CategoryMlWeightDao {
    @Query("SELECT * FROM category_ml_weights WHERE featureKey = :featureKey")
    suspend fun getWeightsForFeature(featureKey: String): List<CategoryMlWeightEntity>

    @Query("SELECT * FROM category_ml_weights WHERE featureKey IN (:featureKeys)")
    suspend fun getWeightsForFeatures(featureKeys: List<String>): List<CategoryMlWeightEntity>

    @Query("SELECT * FROM category_ml_weights")
    suspend fun getAllWeights(): List<CategoryMlWeightEntity>

    @Query("SELECT COUNT(*) FROM category_ml_weights")
    suspend fun getCount(): Int

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(weights: List<CategoryMlWeightEntity>)

    @Query("""
        INSERT INTO category_ml_weights (featureKey, categoryId, count, lastUpdatedEpochMs)
        VALUES (:featureKey, :categoryId, 1, :timestamp)
        ON CONFLICT(featureKey, categoryId) DO UPDATE SET
            count = count + 1,
            lastUpdatedEpochMs = :timestamp
    """)
    suspend fun incrementWeight(
        featureKey: String,
        categoryId: Long,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM category_ml_weights")
    suspend fun deleteAll()
}

