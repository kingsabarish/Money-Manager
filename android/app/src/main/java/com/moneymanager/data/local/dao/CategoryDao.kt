package com.moneymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE parentId IS NULL AND name = :name")
    suspend fun countTopLevelByName(name: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId AND name = :name")
    suspend fun countChildByName(parentId: Long, name: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactions(categoryId: Long): Int

    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Update
    suspend fun update(entity: CategoryEntity)

    @Delete
    suspend fun delete(entity: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    // Restore clears rows child-first: the self-referential RESTRICT foreign key
    // rejects deleting a parent while any child still points at it — even within a
    // single `DELETE FROM categories` statement — so a plain deleteAll() aborts
    // whenever a subcategory exists.
    @Query("DELETE FROM categories WHERE parentId IS NOT NULL")
    suspend fun deleteChildRows()

    @Query("DELETE FROM categories WHERE parentId IS NULL")
    suspend fun deleteTopLevelRows()
}
