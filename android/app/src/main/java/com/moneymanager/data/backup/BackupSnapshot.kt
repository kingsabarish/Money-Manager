package com.moneymanager.data.backup

import kotlinx.serialization.Serializable

/**
 * Portable, schema-resilient snapshot of the entire database. Serialized to JSON
 * for backup rather than shipping the raw SQLite file, so a restore can survive
 * future schema tweaks. Amounts and dates are stored as strings (same on-disk
 * representation Room uses) to avoid any numeric/locale drift.
 *
 * [version] is the snapshot format version (independent of the Room schema
 * version), so an older app can refuse a newer format cleanly.
 */
@Serializable
data class BackupSnapshot(
    val version: Int = FORMAT_VERSION,
    val exportedAtEpochMs: Long,
    val categories: List<CategorySnapshot>,
    val accounts: List<AccountSnapshot>,
    val transactions: List<TransactionSnapshot>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class CategorySnapshot(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val type: String,
)

@Serializable
data class AccountSnapshot(
    val id: Long,
    val name: String,
    val parentId: Long?,
)

@Serializable
data class TransactionSnapshot(
    val id: Long,
    val type: String,
    val date: String,
    val amount: String,
    val categoryId: Long,
    val accountId: Long,
    val note: String?,
    val isApproved: Boolean = true,
)
