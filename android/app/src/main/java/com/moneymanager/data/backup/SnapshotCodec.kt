package com.moneymanager.data.backup

import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.domain.model.TransactionType
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure (no Android/Room/IO) conversion between Room entities and the portable
 * [BackupSnapshot] JSON. Kept side-effect free so it can be unit-tested on the
 * JVM without a device.
 */
object SnapshotCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: BackupSnapshot): String = json.encodeToString(snapshot)

    fun decode(text: String): BackupSnapshot = json.decodeFromString(text)

    fun toSnapshot(
        exportedAtEpochMs: Long,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
    ): BackupSnapshot =
        BackupSnapshot(
            exportedAtEpochMs = exportedAtEpochMs,
            categories =
                categories.map {
                    CategorySnapshot(it.id, it.name, it.parentId, it.type.name)
                },
            accounts =
                accounts.map {
                    AccountSnapshot(it.id, it.name, it.parentId)
                },
            transactions =
                transactions.map {
                    TransactionSnapshot(
                        id = it.id,
                        type = it.type.name,
                        date = it.date.toString(),
                        amount = it.amount.toPlainString(),
                        categoryId = it.categoryId,
                        accountId = it.accountId,
                        note = it.note,
                    )
                },
        )

    /** Categories mapped to entities, parents (parentId == null) first for FK order. */
    fun categoryEntities(snapshot: BackupSnapshot): List<CategoryEntity> =
        snapshot.categories
            .sortedBy { it.parentId != null }
            .map {
                CategoryEntity(
                    id = it.id,
                    name = it.name,
                    parentId = it.parentId,
                    type = TransactionType.valueOf(it.type),
                )
            }

    /** Accounts mapped to entities, parents first for FK order. */
    fun accountEntities(snapshot: BackupSnapshot): List<AccountEntity> =
        snapshot.accounts
            .sortedBy { it.parentId != null }
            .map { AccountEntity(id = it.id, name = it.name, parentId = it.parentId) }

    fun transactionEntities(snapshot: BackupSnapshot): List<TransactionEntity> =
        snapshot.transactions.map {
            TransactionEntity(
                id = it.id,
                type = TransactionType.valueOf(it.type),
                date = LocalDate.parse(it.date),
                amount = BigDecimal(it.amount),
                categoryId = it.categoryId,
                accountId = it.accountId,
                note = it.note,
            )
        }
}
