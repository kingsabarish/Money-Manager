package com.moneymanager.data.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moneymanager.data.categorization.CategorizationEngine
import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for simulated / mock bank debit SMS messages during testing.
 * Can be triggered via ADB shell broadcast or the in-app "Simulate Test Expense" button.
 */
class TestSmsReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestReceiverEntryPoint {
        fun transactionRepository(): TransactionRepository

        fun categorizationEngine(): CategorizationEngine

        fun accountDao(): AccountDao

        fun categoryDao(): CategoryDao

        fun notificationManager(): TransactionNotificationManager
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val body =
            intent.getStringExtra("body")
                ?: "Rs 350.00 debited from A/c XX1234 on 19-Sep-26 at SWIGGY UPI ref 423984"
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        android.util.Log.i("MoneyManager", "TestSmsReceiver received message: $body")

        val parsed = TransactionParser.parseSms(body, timestamp)
        android.util.Log.i("MoneyManager", "TestSmsReceiver parsed result: $parsed")
        if (parsed !is ParsedTransaction.Expense) {
            android.util.Log.w("MoneyManager", "Parsed transaction is not an expense, skipping.")
            return
        }

        val entryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                TestReceiverEntryPoint::class.java,
            )
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Resolve Account
                var allAccounts = entryPoint.accountDao().getAll()
                val account =
                    if (allAccounts.isEmpty()) {
                        val newId = entryPoint.accountDao().insert(
                            com.moneymanager.data.local.entity.AccountEntity(name = "Bank", parentId = null)
                        )
                        entryPoint.accountDao().getById(newId)
                    } else {
                        val matched =
                            if (parsed.accountRef != null) {
                                allAccounts.firstOrNull { it.name.contains(parsed.accountRef, ignoreCase = true) }
                            } else {
                                null
                            }
                        matched ?: allAccounts.first()
                    } ?: return@launch

                android.util.Log.i("MoneyManager", "Resolved account: ${account.name} (id=${account.id})")

                // 2. Resolve or Predict Category
                val allCategories = entryPoint.categoryDao().getAll()
                val finalCategoryId =
                    if (allCategories.isEmpty()) {
                        entryPoint.categoryDao().insert(
                            com.moneymanager.data.local.entity.CategoryEntity(
                                name = "Other",
                                parentId = null,
                                type = com.moneymanager.domain.model.TransactionType.EXPENSE,
                            )
                        )
                    } else {
                        val catResult =
                            entryPoint.categorizationEngine().predictCategory(
                                merchant = parsed.merchant,
                                note = parsed.note,
                                amount = parsed.amount,
                                date = parsed.date,
                            )
                        catResult.finalCategoryId
                    }

                val category = entryPoint.categoryDao().getById(finalCategoryId) ?: return@launch
                android.util.Log.i("MoneyManager", "Resolved category: ${category.name} (id=${category.id})")

                // 3. Save auto-detected expense with isApproved = false
                val reasonableNote =
                    when {
                        TransactionParser.isReasonableNote(parsed.note) -> parsed.note
                        TransactionParser.isReasonableNote(parsed.merchant) -> parsed.merchant
                        else -> null
                    }
                val addResult =
                    entryPoint.transactionRepository().addAutoExpense(
                        amount = parsed.amount,
                        date = parsed.date,
                        categoryId = category.id,
                        accountId = account.id,
                        note = reasonableNote,
                    )

                android.util.Log.i("MoneyManager", "addAutoExpense result: $addResult")

                // 4. Post interactive notification
                if (addResult is AppResult.Success) {
                    android.util.Log.i("MoneyManager", "Posting notification for transaction ${addResult.data.id}")
                    entryPoint.notificationManager().showTransactionNotification(
                        transactionId = addResult.data.id,
                        amount = addResult.data.amount,
                        merchant = parsed.merchant,
                        categoryName = category.name,
                        accountName = account.name,
                        note = addResult.data.note,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MoneyManager", "Error processing test SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
