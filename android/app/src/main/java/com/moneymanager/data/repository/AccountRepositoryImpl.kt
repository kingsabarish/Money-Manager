package com.moneymanager.data.repository

import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.domain.model.Account
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.asSuccess
import com.moneymanager.domain.model.fail
import com.moneymanager.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Room-backed [AccountRepository]; mirrors [CategoryRepositoryImpl]'s invariants. */
class AccountRepositoryImpl
    @Inject
    constructor(
        private val dao: AccountDao,
    ) : AccountRepository {
        override fun observeAll(): Flow<List<Account>> =
            dao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override suspend fun getById(id: Long): AppResult<Account> {
            val entity = dao.getById(id) ?: return fail(AppError.NotFound)
            return entity.toDomain().asSuccess()
        }

        override suspend fun create(name: String, parentId: Long?): AppResult<Account> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return fail(AppError.Validation("Name must not be blank"))
            }

            if (parentId != null) {
                val parent =
                    dao.getById(parentId)
                        ?: return fail(AppError.Validation("Parent account $parentId does not exist"))
                if (parent.parentId != null) {
                    return fail(
                        AppError.Validation(
                            "Accounts are limited to two levels; parent must be top-level",
                        ),
                    )
                }
            }

            val duplicates =
                if (parentId == null) {
                    dao.countTopLevelByName(trimmed)
                } else {
                    dao.countChildByName(parentId, trimmed)
                }
            if (duplicates > 0) {
                return fail(AppError.Conflict("An account with that name already exists"))
            }

            val id = dao.insert(AccountEntity(name = trimmed, parentId = parentId))
            return Account(id = id, name = trimmed, parentId = parentId).asSuccess()
        }

        override suspend fun rename(id: Long, name: String): AppResult<Account> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return fail(AppError.Validation("Name must not be blank"))
            }
            val existing = dao.getById(id) ?: return fail(AppError.NotFound)

            val duplicates =
                if (existing.parentId == null) {
                    dao.countTopLevelByName(trimmed)
                } else {
                    dao.countChildByName(existing.parentId, trimmed)
                }
            if (duplicates > 0 && !trimmed.equals(existing.name, ignoreCase = false)) {
                return fail(AppError.Conflict("An account with that name already exists"))
            }

            val updated = existing.copy(name = trimmed)
            dao.update(updated)
            return updated.toDomain().asSuccess()
        }

        override suspend fun delete(id: Long): AppResult<Unit> {
            val existing = dao.getById(id) ?: return fail(AppError.NotFound)
            if (dao.countChildren(id) > 0) {
                return fail(AppError.Conflict("Delete or reassign its sub-accounts first"))
            }
            if (dao.countTransactions(id) > 0) {
                return fail(AppError.Conflict("Account is used by one or more transactions"))
            }
            dao.delete(existing)
            return Unit.asSuccess()
        }
    }
