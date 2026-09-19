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
                // 1. Resolve Account
                val allAccounts = entryPoint.accountDao().getAll()
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

                android.util.Log.i("MoneyManager", "SMS Resolved account: ${account.name} (id=${account.id})")

                // 2. Predict Category via ML CategorizationEngine
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
                android.util.Log.i("MoneyManager", "SMS Resolved category: ${category.name} (id=${category.id})")

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
                        merchant = parsed.merchant,
                    )

                android.util.Log.i("MoneyManager", "SMS addAutoExpense result: $addResult")

                // 4. Post interactive notification
                if (addResult is AppResult.Success) {
                    android.util.Log.i("MoneyManager", "SMS Posting notification for ${addResult.data.id}")
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
                android.util.Log.e("MoneyManager", "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

