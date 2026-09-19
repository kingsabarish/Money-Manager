package com.moneymanager.di

import android.content.Context
import androidx.room.Room
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
            .addMigrations(MoneyManagerDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideCategoryDao(db: MoneyManagerDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideAccountDao(db: MoneyManagerDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideTransactionDao(db: MoneyManagerDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryMlWeightDao(db: MoneyManagerDatabase): CategoryMlWeightDao = db.categoryMlWeightDao()
}
