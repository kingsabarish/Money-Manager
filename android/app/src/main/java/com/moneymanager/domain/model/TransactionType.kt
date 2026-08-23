package com.moneymanager.domain.model

/**
 * The kind of transaction. Only [EXPENSE] is wired up end to end for now;
 * [INCOME] and [TRANSFER] exist so the schema has room to grow (mirrors the
 * backend enum).
 */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
}
