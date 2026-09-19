package com.moneymanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores association counts between extracted feature keys and categories.
 *
 * [featureKey] is a typed feature string such as `merchant:swiggy`, `time:morning`,
 * or `token:coffee`.
 * [count] is the historical frequency with which this feature co-occurred with [categoryId].
 */
@Entity(
    tableName = "category_ml_weights",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index(value = ["featureKey", "categoryId"], unique = true),
    ],
)
data class CategoryMlWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val featureKey: String,
    val categoryId: Long,
    val count: Int = 1,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
)

