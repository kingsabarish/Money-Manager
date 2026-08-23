package com.moneymanager.data.local.seed

import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts a small set of default categories and accounts the first time the app
 * runs, so the entry screen's pickers are never empty. A no-op once any category
 * exists (including after a restore).
 */
@Singleton
class DatabaseSeeder
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
        private val accountDao: AccountDao,
    ) {
        suspend fun seedIfEmpty() {
            if (categoryDao.getAll().isNotEmpty()) return

            DEFAULT_CATEGORIES.forEach { name ->
                categoryDao.insert(
                    CategoryEntity(name = name, parentId = null, type = TransactionType.EXPENSE),
                )
            }
            DEFAULT_ACCOUNTS.forEach { name ->
                accountDao.insert(AccountEntity(name = name, parentId = null))
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
