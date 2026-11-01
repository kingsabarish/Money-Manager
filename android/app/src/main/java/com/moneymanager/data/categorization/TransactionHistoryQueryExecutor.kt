package com.moneymanager.data.categorization

import android.database.Cursor
import android.util.Log
import com.moneymanager.data.local.MoneyManagerDatabase
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

data class TransactionHistoryRecord(
    val date: String,
    val amount: BigDecimal,
    val merchant: String,
    val note: String,
    val category: String,
    val subcategory: String,
)

data class HistoryQueryResult(
    val records: List<TransactionHistoryRecord>,
    val wasFallback: Boolean,
)

interface TransactionHistoryQueryExecutor {
    suspend fun executeQuery(sql: String?, limit: Int = 75): HistoryQueryResult
}

@Singleton
class TransactionHistoryQueryExecutorImpl @Inject constructor(
    private val database: MoneyManagerDatabase,
) : TransactionHistoryQueryExecutor {

    companion object {
        private const val TAG = "HistoryQueryExecutor"
        private val FORBIDDEN_WORDS = setOf(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER",
            "ATTACH", "DETACH", "PRAGMA", "CREATE", "REPLACE",
            "EXEC", "VACUUM", "TRUNCATE"
        )
        private val FORBIDDEN_REGEX = Regex(
            "\\b(${FORBIDDEN_WORDS.joinToString("|")})\\b",
            RegexOption.IGNORE_CASE
        )
        private val LIMIT_REGEX = Regex("(?i)\\bLIMIT\\s+(\\d+)")
    }

    override suspend fun executeQuery(sql: String?, limit: Int): HistoryQueryResult {
        if (sql.isNullOrBlank()) {
            Log.d(TAG, "Empty or null query; running fallback.")
            return executeFallback(limit)
        }

        val cleanSql = sql.trim().replace(Regex(";+$"), "").trim()

        // 1. Must be a SELECT query
        if (!cleanSql.startsWith("SELECT", ignoreCase = true)) {
            Log.w(TAG, "Rejected non-SELECT query: $cleanSql")
            return executeFallback(limit)
        }

        // 2. Reject mutating or dangerous keywords
        if (FORBIDDEN_REGEX.containsMatchIn(cleanSql)) {
            Log.w(TAG, "Rejected query containing forbidden keywords: $cleanSql")
            return executeFallback(limit)
        }

        // 3. Enforce limit constraint
        val finalSql = enforceLimit(cleanSql, limit)

        return try {
            val cursor = database.openHelper.readableDatabase.query(finalSql)
            val records = parseCursor(cursor)
            if (records.isEmpty()) {
                Log.d(TAG, "Query returned 0 rows: $finalSql; executing fallback.")
                executeFallback(limit)
            } else {
                Log.d(TAG, "Query successfully returned ${records.size} rows.")
                HistoryQueryResult(records = records, wasFallback = false)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Query execution failed: $finalSql", e)
            executeFallback(limit)
        }
    }

    private fun enforceLimit(sql: String, maxLimit: Int): String {
        val match = LIMIT_REGEX.find(sql)
        return if (match != null) {
            val currentLimit = match.groupValues[1].toIntOrNull() ?: maxLimit
            if (currentLimit > maxLimit) {
                sql.replaceRange(match.range, "LIMIT $maxLimit")
            } else {
                sql
            }
        } else {
            "$sql LIMIT $maxLimit"
        }
    }

    private fun executeFallback(limit: Int): HistoryQueryResult {
        val fallbackSql = """
            SELECT merchant, amount, category, subcategory, note, date 
            FROM v_transaction_history 
            ORDER BY date DESC, id DESC 
            LIMIT $limit
        """.trimIndent()

        return try {
            val cursor = database.openHelper.readableDatabase.query(fallbackSql)
            val records = parseCursor(cursor)
            HistoryQueryResult(records = records, wasFallback = true)
        } catch (e: Throwable) {
            Log.e(TAG, "Fallback query failed", e)
            HistoryQueryResult(records = emptyList(), wasFallback = true)
        }
    }

    private fun parseCursor(cursor: Cursor): List<TransactionHistoryRecord> {
        val records = mutableListOf<TransactionHistoryRecord>()
        cursor.use { c ->
            val merchantCol = c.getColumnIndex("merchant")
            val amountCol = c.getColumnIndex("amount")
            val categoryCol = c.getColumnIndex("category")
            val subcategoryCol = c.getColumnIndex("subcategory")
            val noteCol = c.getColumnIndex("note")
            val dateCol = c.getColumnIndex("date")

            while (c.moveToNext()) {
                val merchant = if (merchantCol >= 0) c.getString(merchantCol) ?: "" else ""
                val amountStr = if (amountCol >= 0) c.getString(amountCol) ?: "0" else "0"
                val amount = try { BigDecimal(amountStr) } catch (_: Exception) { BigDecimal.ZERO }
                val category = if (categoryCol >= 0) c.getString(categoryCol) ?: "" else ""
                val subcategory = if (subcategoryCol >= 0) c.getString(subcategoryCol) ?: "" else ""
                val note = if (noteCol >= 0) c.getString(noteCol) ?: "" else ""
                val date = if (dateCol >= 0) c.getString(dateCol) ?: "" else ""

                records.add(
                    TransactionHistoryRecord(
                        date = date,
                        amount = amount,
                        merchant = merchant,
                        note = note,
                        category = category,
                        subcategory = subcategory,
                    )
                )
            }
        }
        return records
    }
}

