package com.moneymanager.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneymanager.data.local.MoneyManagerDatabase
import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.CategoryMlWeightDao
import com.moneymanager.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MoneyManagerDatabase =
        Room.databaseBuilder(
            context,
            MoneyManagerDatabase::class.java,
            MoneyManagerDatabase.NAME,
        )
            .addMigrations(
                MoneyManagerDatabase.MIGRATION_1_2,
                MoneyManagerDatabase.MIGRATION_2_3,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    createTransactionHistoryView(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    createTransactionHistoryView(db)
                }
            })
            .fallbackToDestructiveMigration(dropAllTables = true)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    private fun createTransactionHistoryView(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIEW IF NOT EXISTS v_transaction_history AS
            SELECT 
                t.id AS id,
                t.date AS date,
                t.amount AS amount,
                COALESCE(t.merchant, '') AS merchant,
                COALESCE(t.note, '') AS note,
                COALESCE(p.name, c.name) AS category,
                CASE WHEN p.name IS NOT NULL THEN c.name ELSE 'General' END AS subcategory
            FROM transactions t
            LEFT JOIN categories c ON t.categoryId = c.id
            LEFT JOIN categories p ON c.parentId = p.id
            WHERE t.isApproved = 1
            """.trimIndent(),
        )
    }

    @Provides
    fun provideCategoryDao(db: MoneyManagerDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideAccountDao(db: MoneyManagerDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideTransactionDao(db: MoneyManagerDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryMlWeightDao(db: MoneyManagerDatabase): CategoryMlWeightDao = db.categoryMlWeightDao()
}
