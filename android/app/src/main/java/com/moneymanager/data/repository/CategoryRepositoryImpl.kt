package com.moneymanager.data.repository

import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.AppError
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.model.TransactionType
import com.moneymanager.domain.model.asSuccess
import com.moneymanager.domain.model.fail
import com.moneymanager.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [CategoryRepository]. Enforces the invariants that the backend
 * enforced in its route layer (two-level depth, duplicate name per parent,
 * guarded delete), since on-device there is no server behind it.
 */
class CategoryRepositoryImpl
    @Inject
    constructor(
        private val dao: CategoryDao,
    ) : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> =
            dao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override suspend fun getById(id: Long): AppResult<Category> {
            val entity = dao.getById(id) ?: return fail(AppError.NotFound)
            return entity.toDomain().asSuccess()
        }

        override suspend fun create(
            name: String,
            parentId: Long?,
            type: TransactionType,
        ): AppResult<Category> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return fail(AppError.Validation("Name must not be blank"))
            }

            if (parentId != null) {
                val parent =
                    dao.getById(parentId)
                        ?: return fail(AppError.Validation("Parent category $parentId does not exist"))
                // Two-level limit: the parent must itself be top-level.
                if (parent.parentId != null) {
                    return fail(
                        AppError.Validation(
                            "Categories are limited to two levels; parent must be top-level",
                        ),
                    )
                }
            }

            // Name must be unique within the same parent. SQLite treats NULL
            // parentIds as distinct, so a unique index can't cover the top-level
            // case — check explicitly, matching the backend.
            val duplicates =
                if (parentId == null) {
                    dao.countTopLevelByName(trimmed)
                } else {
                    dao.countChildByName(parentId, trimmed)
                }
            if (duplicates > 0) {
                return fail(AppError.Conflict("A category with that name already exists"))
            }

            val id =
                dao.insert(CategoryEntity(name = trimmed, parentId = parentId, type = type))
            return Category(id = id, name = trimmed, parentId = parentId, type = type).asSuccess()
        }

        override suspend fun rename(id: Long, name: String): AppResult<Category> {
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
            // A match on the row's own current name is fine (no-op rename).
            if (duplicates > 0 && !trimmed.equals(existing.name, ignoreCase = false)) {
                return fail(AppError.Conflict("A category with that name already exists"))
            }

            val updated = existing.copy(name = trimmed)
            dao.update(updated)
            return updated.toDomain().asSuccess()
        }

        override suspend fun delete(id: Long): AppResult<Unit> {
            val existing = dao.getById(id) ?: return fail(AppError.NotFound)
            if (dao.countChildren(id) > 0) {
                return fail(AppError.Conflict("Delete or reassign its subcategories first"))
            }
            if (dao.countTransactions(id) > 0) {
                return fail(AppError.Conflict("Category is used by one or more transactions"))
            }
            dao.delete(existing)
            return Unit.asSuccess()
        }
    }
