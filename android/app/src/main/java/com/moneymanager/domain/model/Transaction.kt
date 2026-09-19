package com.moneymanager.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A single money entry. Only expense entries are created for now.
 *
 * [amount] is a [BigDecimal] (never a floating-point type) and is stored as
 * TEXT on disk. [date] is a calendar day with no time zone.
 */
data class Transaction(
    val id: Long,
    val type: TransactionType,
    val date: LocalDate,
    val amount: BigDecimal,
    val categoryId: Long,
    val accountId: Long,
    val note: String?,
    val isApproved: Boolean = true,
)
