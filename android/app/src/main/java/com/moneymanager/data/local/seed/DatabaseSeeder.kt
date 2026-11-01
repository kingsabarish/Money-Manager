package com.moneymanager.data.local.seed

import com.moneymanager.data.categorization.CategorizationEngine
import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts a small set of default categories and accounts the first time the app
 * runs, so the entry screen's pickers are never empty. A no-op once any category
 * exists (including after a restore). Also seeds ML categorization weights and
 * normalizes travel categories.
 */
@Singleton
class DatabaseSeeder
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
        private val accountDao: AccountDao,
        private val transactionDao: TransactionDao,
        private val categorizationEngine: CategorizationEngine,
    ) {
        suspend fun seedIfEmpty() {
            if (categoryDao.getAll().isEmpty()) {
                DEFAULT_CATEGORIES.forEach { name ->
                    categoryDao.insert(
                        CategoryEntity(name = name, parentId = null, type = TransactionType.EXPENSE),
                    )
                }
                DEFAULT_ACCOUNTS.forEach { name ->
                    accountDao.insert(AccountEntity(name = name, parentId = null))
                }
            }

            normalizeDatabaseCategories()

            val transactions = transactionDao.getAll()
            if (transactions.isNotEmpty()) {
                categorizationEngine.seedFromTransactions(transactions, categoryDao.getAll())
            } else {
                categorizationEngine.seedInitialWeights()
            }
        }

        private suspend fun normalizeDatabaseCategories() {
            val categories = categoryDao.getAll()
            if (categories.isEmpty()) return

            // 1. Rename Bus -> Public Transport
            val bus = categories.firstOrNull { it.name.equals("Bus", ignoreCase = true) }
            if (bus != null) {
                categoryDao.update(bus.copy(name = "Public Transport"))
            }

            // 2. Combine cab, auto, Rapido -> Cab / Auto
            val cabList =
                categories.filter {
                    it.name.equals("cab", ignoreCase = true) ||
                        it.name.equals("auto", ignoreCase = true) ||
                        it.name.equals("rapido", ignoreCase = true) ||
                        it.name.equals("Cab / Auto", ignoreCase = true)
                }
            if (cabList.size > 1) {
                val primary = cabList.first()
                categoryDao.update(primary.copy(name = "Cab / Auto"))
                val redundant = cabList.drop(1)
                for (r in redundant) {
                    transactionDao.remapCategory(oldCategoryId = r.id, newCategoryId = primary.id)
                    categoryDao.delete(r)
                }
            } else if (cabList.size == 1 && !cabList.first().name.equals("Cab / Auto", ignoreCase = true)) {
                categoryDao.update(cabList.first().copy(name = "Cab / Auto"))
            }
        }

        private companion object {
            val DEFAULT_CATEGORIES =
                listOf(
                    "Food",
                    "Transport",
                    "Housing",
                    "Utilities",
                    "Entertainment",
                    "Health",
                    "Shopping",
                    "Other",
                )
            val DEFAULT_ACCOUNTS = listOf("Cash", "Bank", "Credit Card")
        }
    }
