package com.moneymanager.data.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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

class SmsTransactionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun transactionRepository(): TransactionRepository

        fun categorizationEngine(): CategorizationEngine

        fun accountResolutionEngine(): com.moneymanager.data.account.AccountResolutionEngine

        fun accountDao(): AccountDao

        fun categoryDao(): CategoryDao

        fun notificationManager(): TransactionNotificationManager
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        android.util.Log.i("MoneyManager", "SMS received: $fullBody")
        val parsed = TransactionParser.parseSms(fullBody, timestamp)
        android.util.Log.i("MoneyManager", "Parsed SMS result: $parsed")
        if (parsed !is ParsedTransaction.Expense) return

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SmsReceiverEntryPoint::class.java)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Resolve Account via dynamic AccountResolutionEngine
                val account = entryPoint.accountResolutionEngine().resolveAccount(
                    rawText = parsed.rawText,
                    accountRef = parsed.accountRef,
                    merchant = parsed.merchant,
                    amount = parsed.amount,
                )

                android.util.Log.i("MoneyManager", "SMS Resolved account: ${account.name} (id=${account.id})")

                // 2. Predict Category, Subcategory, Note, and Payee via ReAct agent
                val catResult =
                    entryPoint.categorizationEngine().predictCategory(
                        rawMessage = parsed.rawText,
                        source = parsed.source,
                        amount = parsed.amount,
                        fallbackMerchant = parsed.merchant,
                        fallbackNote = parsed.note,
                        time = parsed.time ?: java.time.LocalTime.now(),
                        date = parsed.date,
                    )
                val category = entryPoint.categoryDao().getById(catResult.finalCategoryId) ?: return@launch
                val noteToSave = catResult.predictedNote.takeIf { it.isNotBlank() }
                val merchantToSave = if (catResult.confidence == 0f) parsed.rawText else catResult.predictedMerchant
                val aiNotesToSave = catResult.categorizationNotes
                android.util.Log.i("MoneyManager", "SMS Resolved category: ${category.name} (id=${category.id}), merchant: $merchantToSave, note: $noteToSave, aiNotes: $aiNotesToSave")

                // 3. Save auto-detected expense with isApproved = false
                val addResult =
                    entryPoint.transactionRepository().addAutoExpense(
                        amount = parsed.amount,
                        date = parsed.date,
                        categoryId = category.id,
                        accountId = account.id,
                        note = noteToSave,
                        merchant = merchantToSave,
                        categorizationNotes = aiNotesToSave,
                    )

                android.util.Log.i("MoneyManager", "SMS addAutoExpense result: $addResult")

                // 4. Post interactive notification
                if (addResult is AppResult.Success) {
                    val notifMerchant = if (catResult.confidence == 0f) parsed.merchant else merchantToSave
                    android.util.Log.i("MoneyManager", "SMS Posting notification for ${addResult.data.id}")
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
                android.util.Log.e("MoneyManager", "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
