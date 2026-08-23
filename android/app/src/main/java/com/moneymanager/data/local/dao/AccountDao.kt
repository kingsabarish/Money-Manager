package com.moneymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE parentId IS NULL AND name = :name")
    suspend fun countTopLevelByName(name: String): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE parentId = :parentId AND name = :name")
    suspend fun countChildByName(parentId: Long, name: String): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countTransactions(accountId: Long): Int

    @Insert
    suspend fun insert(entity: AccountEntity): Long

    @Insert
    suspend fun insertAll(entities: List<AccountEntity>)

    @Update
    suspend fun update(entity: AccountEntity)

    @Delete
    suspend fun delete(entity: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    // Restore clears rows child-first: the self-referential RESTRICT foreign key
    // rejects deleting a parent while any child still points at it — even within a
    // single `DELETE FROM accounts` statement — so a plain deleteAll() aborts
    // whenever a sub-account exists.
    @Query("DELETE FROM accounts WHERE parentId IS NOT NULL")
    suspend fun deleteChildRows()

    @Query("DELETE FROM accounts WHERE parentId IS NULL")
    suspend fun deleteTopLevelRows()
}
