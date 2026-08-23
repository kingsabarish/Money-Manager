package com.moneymanager.di

import com.moneymanager.data.repository.AccountRepositoryImpl
import com.moneymanager.data.repository.CategoryRepositoryImpl
import com.moneymanager.data.repository.TransactionRepositoryImpl
import com.moneymanager.domain.repository.AccountRepository
import com.moneymanager.domain.repository.CategoryRepository
import com.moneymanager.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds repository interfaces to their Room-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository
}
