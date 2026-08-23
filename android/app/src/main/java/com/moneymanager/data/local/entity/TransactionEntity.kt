package com.moneymanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymanager.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Room row for a transaction (entry).
 *
 * [amount] is stored as TEXT via the BigDecimal converter — never a REAL/Double
 * column, to avoid binary floating-point rounding of money. [date] is stored as
 * an ISO `yyyy-MM-dd` string. The category/account FKs use RESTRICT so a row
 * that is in use cannot be deleted out from under an entry.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("accountId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val date: LocalDate,
    val amount: BigDecimal,
    val categoryId: Long,
    val accountId: Long,
    val note: String?,
)
