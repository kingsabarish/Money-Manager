package com.moneymanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneymanager.data.local.converter.Converters
import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.CategoryMlWeightDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.CategoryMlWeightEntity
import com.moneymanager.data.local.entity.TransactionEntity

/**
 * The on-device database — the single source of truth for all app data.
 *
 * Schema is exported to `app/schemas/` (see the `room.schemaLocation` KSP arg)
 * so migrations can be authored once the schema starts changing.
 */
@Database(
    entities = [
        CategoryEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        CategoryMlWeightEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MoneyManagerDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryMlWeightDao(): CategoryMlWeightDao

    companion object {
        const val NAME = "money_manager.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isApproved INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isApproved ON transactions(isApproved)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_ml_weights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        featureKey TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        count INTEGER NOT NULL,
                        lastUpdatedEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_category_ml_weights_categoryId ON category_ml_weights(categoryId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_category_ml_weights_featureKey_categoryId ON category_ml_weights(featureKey, categoryId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN merchant TEXT")
            }
        }
    }
}
