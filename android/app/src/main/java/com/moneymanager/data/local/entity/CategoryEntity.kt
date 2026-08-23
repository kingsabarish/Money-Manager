package com.moneymanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymanager.domain.model.TransactionType

/**
 * Room row for a category. Self-referential: [parentId] points at another row
 * in this table (a top-level category has [parentId] == null).
 *
 * The FK uses RESTRICT so the database refuses to delete a parent that still
 * has children; the repository turns that into a friendly Conflict error.
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("parentId"), Index("name")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long?,
    val type: TransactionType = TransactionType.EXPENSE,
)
