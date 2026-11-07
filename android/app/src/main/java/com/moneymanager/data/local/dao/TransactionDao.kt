package com.moneymanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Insert
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE isApproved = 1 ORDER BY date DESC, id DESC")
    fun observeApproved(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isApproved = 0 ORDER BY date DESC, id DESC")
    fun observeUnapproved(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE isApproved = 0")
    fun observeUnapprovedCount(): Flow<Int>

    @Query("UPDATE transactions SET isApproved = :approved WHERE id = :id")
    suspend fun setApproved(id: Long, approved: Boolean)

    @Query("UPDATE transactions SET categorizationNotes = :notes WHERE id = :id")
    suspend fun updateCategorizationNotes(id: Long, notes: String?)

    @Query("UPDATE transactions SET isApproved = 1 WHERE isApproved = 0")
    suspend fun approveAll()

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE amount = :amount AND date = :date AND (note = :note OR (:note IS NULL AND note IS NULL)) LIMIT 1")
    suspend fun findMatching(amount: java.math.BigDecimal, date: java.time.LocalDate, note: String?): TransactionEntity?

    @Query("SELECT accountId FROM transactions WHERE merchant = :merchant AND isApproved = 1 ORDER BY date DESC, id DESC LIMIT 5")
    suspend fun getRecentAccountIdsForMerchant(merchant: String): List<Long>

    @Query("SELECT accountId FROM transactions WHERE isApproved = 1 GROUP BY accountId ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostUsedAccountId(): Long?

    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun remapCategory(oldCategoryId: Long, newCategoryId: Long)

    @Query("SELECT * FROM transactions WHERE isApproved = 1 ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getRecentApproved(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE merchant = :merchant AND isApproved = 1 ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getApprovedByMerchant(merchant: String, limit: Int): List<TransactionEntity>

    @Query(
        """
        SELECT 
            t.date AS date,
            t.amount AS amount,
            COALESCE(t.merchant, '') AS merchant,
            COALESCE(t.note, '') AS note,
            COALESCE(p.name, c.name) AS category,
            CASE WHEN p.name IS NOT NULL THEN c.name ELSE 'General' END AS subcategory,
            t.categorizationNotes AS categorizationNotes
        FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        LEFT JOIN categories p ON c.parentId = p.id
        WHERE t.isApproved = 1 AND t.date >= :sinceDate
        ORDER BY t.date ASC, t.id ASC
        """
    )
    suspend fun getHistorySince(sinceDate: java.time.LocalDate): List<TransactionHistoryRecord>
}

data class TransactionHistoryRecord(
    val date: String,
    val amount: String,
    val merchant: String,
    val note: String,
    val category: String,
    val subcategory: String,
    val categorizationNotes: String?,
)
