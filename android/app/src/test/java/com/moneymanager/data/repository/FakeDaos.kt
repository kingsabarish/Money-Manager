package com.moneymanager.data.repository

import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Tiny in-memory stand-in for the Room database, shared by the fake DAOs so that
 * (for example) a fake CategoryDao can answer "how many transactions use this
 * category?" against the same transaction list a test set up.
 *
 * This lets the repository invariants be tested as plain JVM unit tests — no
 * emulator or instrumented test required.
 */
class InMemoryDb {
    val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())

    private var nextCategoryId = 1L
    private var nextAccountId = 1L
    private var nextTransactionId = 1L

    fun nextCategoryId() = nextCategoryId++

    fun nextAccountId() = nextAccountId++

    fun nextTransactionId() = nextTransactionId++
}

class FakeCategoryDao(private val db: InMemoryDb) : CategoryDao {
    override fun observeAll(): Flow<List<CategoryEntity>> =
        db.categories.map { list -> list.sortedBy { it.name } }

    override suspend fun getAll(): List<CategoryEntity> = db.categories.value.sortedBy { it.name }

    override suspend fun getById(id: Long): CategoryEntity? =
        db.categories.value.firstOrNull { it.id == id }

    override suspend fun countTopLevelByName(name: String): Int =
        db.categories.value.count { it.parentId == null && it.name == name }

    override suspend fun countChildByName(parentId: Long, name: String): Int =
        db.categories.value.count { it.parentId == parentId && it.name == name }

    override suspend fun countChildren(parentId: Long): Int =
        db.categories.value.count { it.parentId == parentId }

    override suspend fun countTransactions(categoryId: Long): Int =
        db.transactions.value.count { it.categoryId == categoryId }

    override suspend fun insert(entity: CategoryEntity): Long {
        val id = db.nextCategoryId()
        db.categories.value = db.categories.value + entity.copy(id = id)
        return id
    }

    override suspend fun insertAll(entities: List<CategoryEntity>) {
        db.categories.value = db.categories.value + entities
    }

    override suspend fun update(entity: CategoryEntity) {
        db.categories.value = db.categories.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(entity: CategoryEntity) {
        db.categories.value = db.categories.value.filterNot { it.id == entity.id }
    }

    override suspend fun deleteAll() {
        db.categories.value = emptyList()
    }
}

class FakeAccountDao(private val db: InMemoryDb) : AccountDao {
    override fun observeAll(): Flow<List<AccountEntity>> =
        db.accounts.map { list -> list.sortedBy { it.name } }

    override suspend fun getAll(): List<AccountEntity> = db.accounts.value.sortedBy { it.name }

    override suspend fun getById(id: Long): AccountEntity? =
        db.accounts.value.firstOrNull { it.id == id }

    override suspend fun countTopLevelByName(name: String): Int =
        db.accounts.value.count { it.parentId == null && it.name == name }

    override suspend fun countChildByName(parentId: Long, name: String): Int =
        db.accounts.value.count { it.parentId == parentId && it.name == name }

    override suspend fun countChildren(parentId: Long): Int =
        db.accounts.value.count { it.parentId == parentId }

    override suspend fun countTransactions(accountId: Long): Int =
        db.transactions.value.count { it.accountId == accountId }

    override suspend fun insert(entity: AccountEntity): Long {
        val id = db.nextAccountId()
        db.accounts.value = db.accounts.value + entity.copy(id = id)
        return id
    }

    override suspend fun insertAll(entities: List<AccountEntity>) {
        db.accounts.value = db.accounts.value + entities
    }

    override suspend fun update(entity: AccountEntity) {
        db.accounts.value = db.accounts.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(entity: AccountEntity) {
        db.accounts.value = db.accounts.value.filterNot { it.id == entity.id }
    }

    override suspend fun deleteAll() {
        db.accounts.value = emptyList()
    }
}

class FakeTransactionDao(private val db: InMemoryDb) : TransactionDao {
    override fun observeAll(): Flow<List<TransactionEntity>> = db.transactions.asStateFlow()

    override suspend fun getAll(): List<TransactionEntity> = db.transactions.value

    override suspend fun getById(id: Long): TransactionEntity? =
        db.transactions.value.firstOrNull { it.id == id }

    override suspend fun insert(entity: TransactionEntity): Long {
        val id = db.nextTransactionId()
        db.transactions.value = db.transactions.value + entity.copy(id = id)
        return id
    }

    override suspend fun insertAll(entities: List<TransactionEntity>) {
        db.transactions.value = db.transactions.value + entities
    }

    override suspend fun update(entity: TransactionEntity) {
        db.transactions.value = db.transactions.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(entity: TransactionEntity) {
        db.transactions.value = db.transactions.value.filterNot { it.id == entity.id }
    }

    override suspend fun deleteAll() {
        db.transactions.value = emptyList()
    }
}
