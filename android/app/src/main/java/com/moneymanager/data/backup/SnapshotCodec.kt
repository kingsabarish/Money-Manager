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
                        isApproved = it.isApproved,
                        merchant = it.merchant,
                    )
                },
        )

    /** Categories mapped to entities, parents (parentId == null) first for FK order. */
    fun categoryEntities(snapshot: BackupSnapshot): List<CategoryEntity> {
        val rawCategories = snapshot.categories.sortedBy { it.parentId != null }
        val busCategory = rawCategories.firstOrNull { it.name.equals("Bus", ignoreCase = true) }
        val cabCategories = rawCategories.filter {
            it.name.equals("cab", ignoreCase = true) ||
                it.name.equals("auto", ignoreCase = true) ||
                it.name.equals("rapido", ignoreCase = true) ||
                it.name.equals("Cab / Auto", ignoreCase = true)
        }
        val targetCabAuto = cabCategories.firstOrNull()
        val redundantCabIds = cabCategories.drop(1).map { it.id }.toSet()

        return rawCategories
            .filterNot { it.id in redundantCabIds }
            .map {
                val normalizedName =
                    when {
                        it.id == busCategory?.id -> "Public Transport"
                        it.id == targetCabAuto?.id -> "Cab / Auto"
                        else -> it.name
                    }
                CategoryEntity(
                    id = it.id,
                    name = normalizedName,
                    parentId = it.parentId,
                    type = TransactionType.valueOf(it.type),
                )
            }
    }

    /** Accounts mapped to entities, parents first for FK order. */
    fun accountEntities(snapshot: BackupSnapshot): List<AccountEntity> =
        snapshot.accounts
            .sortedBy { it.parentId != null }
            .map { AccountEntity(id = it.id, name = it.name, parentId = it.parentId) }

    fun transactionEntities(snapshot: BackupSnapshot): List<TransactionEntity> {
        val rawCategories = snapshot.categories
        val cabCategories = rawCategories.filter {
            it.name.equals("cab", ignoreCase = true) ||
                it.name.equals("auto", ignoreCase = true) ||
                it.name.equals("rapido", ignoreCase = true) ||
                it.name.equals("Cab / Auto", ignoreCase = true)
        }
        val targetCabAutoId = cabCategories.firstOrNull()?.id
        val redundantCabIds = cabCategories.drop(1).map { it.id }.toSet()

        return snapshot.transactions.map {
            val normalizedCatId =
                if (it.categoryId in redundantCabIds && targetCabAutoId != null) {
                    targetCabAutoId
                } else {
                    it.categoryId
                }
            TransactionEntity(
                id = it.id,
                type = TransactionType.valueOf(it.type),
                date = LocalDate.parse(it.date),
                amount = BigDecimal(it.amount),
                categoryId = normalizedCatId,
                accountId = it.accountId,
                note = it.note,
                isApproved = it.isApproved,
                merchant = it.merchant,
            )
        }
    }
}
