package com.moneymanager.data.repository

import androidx.room.withTransaction
import com.moneymanager.data.backup.BackupSnapshot
import com.moneymanager.data.backup.SnapshotCodec
import com.moneymanager.data.local.MoneyManagerDatabase
import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.asSuccess
import com.moneymanager.domain.model.fail
import javax.inject.Inject

/**
 * Reads every table into a [BackupSnapshot] on export, and on import replaces
 * the whole database from a snapshot inside a single transaction (all-or-nothing).
 *
 * Rows are cleared child-table-first and re-inserted parent-first so the
 * self-referential and cross-table foreign keys never see a dangling reference.
 * IDs from the snapshot are preserved, keeping transaction→category/account
 * references intact across a restore.
 */
class BackupRepositoryImpl
    @Inject
    constructor(
        private val db: MoneyManagerDatabase,
        private val categoryDao: CategoryDao,
        private val accountDao: AccountDao,
        private val transactionDao: TransactionDao,
        private val categorizationEngine: com.moneymanager.data.categorization.CategorizationEngine,
    ) : com.moneymanager.domain.repository.BackupRepository {
        override suspend fun exportToJson(): AppResult<String> =
            runCatching {
                val snapshot =
                    SnapshotCodec.toSnapshot(
                        exportedAtEpochMs = System.currentTimeMillis(),
                        categories = categoryDao.getAll(),
                        accounts = accountDao.getAll(),
                        transactions = transactionDao.getAll(),
                    )
                SnapshotCodec.encode(snapshot)
            }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { fail(AppError.Unknown(it.message ?: "Export failed")) },
            )

        override suspend fun importFromJson(json: String): AppResult<Unit> {
            val snapshot =
                runCatching { SnapshotCodec.decode(json) }
                    .getOrElse {
                        return fail(AppError.Validation("Not a valid backup file"))
                    }
            if (snapshot.version > BackupSnapshot.FORMAT_VERSION) {
                return fail(
                    AppError.Validation(
                        "This backup was made by a newer app version and can't be restored",
                    ),
                )
            }

            return runCatching {
                db.withTransaction {
                    // Clear child-first to respect foreign keys. The self-referential
                    // categories/accounts FKs use RESTRICT, which is enforced per row —
                    // so sub-rows must go before their top-level parents (a blanket
                    // deleteAll() aborts the moment a parent with a child is hit).
                    transactionDao.deleteAll()
                    accountDao.deleteChildRows()
                    accountDao.deleteTopLevelRows()
                    categoryDao.deleteChildRows()
                    categoryDao.deleteTopLevelRows()
                    // Re-insert parent-first (codec orders top-level rows ahead of children).
                    categoryDao.insertAll(SnapshotCodec.categoryEntities(snapshot))
                    accountDao.insertAll(SnapshotCodec.accountEntities(snapshot))
                    transactionDao.insertAll(SnapshotCodec.transactionEntities(snapshot))
                }
                categorizationEngine.seedFromTransactions(
                    SnapshotCodec.transactionEntities(snapshot),
                    SnapshotCodec.categoryEntities(snapshot),
                )
            }.fold(
                onSuccess = { Unit.asSuccess() },
                onFailure = { fail(AppError.Unknown(it.message ?: "Restore failed")) },
            )
        }
    }
