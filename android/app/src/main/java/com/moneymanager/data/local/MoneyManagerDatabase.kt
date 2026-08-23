package com.moneymanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moneymanager.data.local.converter.Converters
import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.TransactionEntity

/**
 * The on-device database — the single source of truth for all app data.
 *
 * Schema is exported to `app/schemas/` (see the `room.schemaLocation` KSP arg)
 * so migrations can be authored once the schema starts changing. Until then,
 * bumping [version] requires a destructive recreation of the DB.
 */
@Database(
    entities = [
        CategoryEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MoneyManagerDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "money_manager.db"
    }
}
