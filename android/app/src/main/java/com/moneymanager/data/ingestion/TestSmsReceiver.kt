package com.moneymanager.data.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moneymanager.data.account.AccountResolutionEngine
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
 * Debug BroadcastReceiver to simulate incoming SMS for manual verification.
 * Triggered via ADB:
 * adb shell am broadcast -a com.moneymanager.action.TEST_SMS --es body "Rs 450.00 debited from A/c XX4006 at SWIGGY for Dinner"
 */
class TestSmsReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestReceiverEntryPoint {
        fun transactionRepository(): TransactionRepository

        fun categorizationEngine(): CategorizationEngine

        fun accountResolutionEngine(): AccountResolutionEngine

        fun accountDao(): AccountDao

        fun categoryDao(): CategoryDao

        fun notificationManager(): TransactionNotificationManager
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != "com.moneymanager.action.TEST_SMS") return

        val body = intent.getStringExtra("body") ?: return
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        android.util.Log.i("MoneyManager", "TestSmsReceiver received body: $body")

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
                // 1. Resolve Account via dynamic AccountResolutionEngine
                val account =
                    entryPoint.accountResolutionEngine().resolveAccount(
                        rawText = parsed.rawText,
                        accountRef = parsed.accountRef,
                        merchant = parsed.merchant,
                        amount = parsed.amount,
                    )

                android.util.Log.i("MoneyManager", "Resolved account: ${account.name} (id=${account.id})")

                // 2. Resolve or Predict Category, Subcategory, Note, and Payee via ReAct agent
                val isSupported = entryPoint.categorizationEngine().isAiCoreSupported()
                val catResult =
                    entryPoint.categorizationEngine().predictCategory(
                        rawMessage = parsed.rawText,
                        source = parsed.source,
                        amount = parsed.amount,
                        fallbackMerchant = parsed.merchant,
                        fallbackNote = parsed.note,
                        date = parsed.date,
                    )
                val category = entryPoint.categoryDao().getById(catResult.finalCategoryId) ?: return@launch
                val noteToSave = catResult.predictedNote.takeIf { it.isNotBlank() }
                val merchantToSave = if (catResult.confidence == 0f && isSupported) parsed.rawText else catResult.predictedMerchant
                android.util.Log.i("MoneyManager", "Resolved category: ${category.name} (id=${category.id}), merchant: $merchantToSave, note: $noteToSave")

                // 3. Save auto-detected expense with isApproved = false
                val addResult =
                    entryPoint.transactionRepository().addAutoExpense(
                        amount = parsed.amount,
                        date = parsed.date,
                        categoryId = category.id,
                        accountId = account.id,
                        note = noteToSave,
                        merchant = merchantToSave,
                    )
                android.util.Log.i("MoneyManager", "addAutoExpense result: $addResult")

                // 4. Post interactive notification
                if (addResult is AppResult.Success) {
                    val notifMerchant = if (catResult.confidence == 0f && isSupported) parsed.merchant else merchantToSave
                    android.util.Log.i("MoneyManager", "Posting notification for transaction ${addResult.data.id}")
                    entryPoint.notificationManager().showTransactionNotification(
                        transactionId = addResult.data.id,
                        amount = addResult.data.amount,
                        merchant = notifMerchant,
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
