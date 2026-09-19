package com.moneymanager.data.ingestion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.TransactionType
import java.util.Collections

class TransactionNotificationListenerService : NotificationListenerService() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun transactionRepository(): TransactionRepository

        fun categorizationEngine(): CategorizationEngine

        fun accountDao(): AccountDao

        fun categoryDao(): CategoryDao

        fun transactionDao(): TransactionDao

        fun notificationManager(): TransactionNotificationManager
    }

    private val processedNotificationKeys = Collections.synchronizedSet(mutableSetOf<String>())

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("MoneyManager", "TransactionNotificationListenerService connected")
        try {
            val active = activeNotifications ?: return
            android.util.Log.i("MoneyManager", "Scanning ${active.size} active notifications on connect")
            for (sbn in active) {
                processStatusBarNotification(sbn)
            }
        } catch (e: Exception) {
            android.util.Log.e("MoneyManager", "Error processing active notifications on connect", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        processStatusBarNotification(sbn)
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        // Only process Google Pay packages
        if (pkg != GPAY_INDIA_PACKAGE && pkg != GPAY_GLOBAL_PACKAGE) return

        val extras = sbn.notification.extras ?: return
        val title =
            (extras.getCharSequence("android.title")
                ?: extras.getCharSequence("android.title.big"))?.toString() ?: ""
        val text =
            (extras.getCharSequence("android.bigText")
                ?: extras.getCharSequence("android.text"))?.toString() ?: ""

        android.util.Log.i("MoneyManager", "GPay notification received from $pkg: title='$title', text='$text'")

        val parsed =
            TransactionParser.parseGPayNotification(
                title = title,
                text = text,
                timestampEpochMs = sbn.postTime,
            )

        android.util.Log.i("MoneyManager", "GPay notification parsed result: $parsed")
        if (parsed !is ParsedTransaction.Expense) return

        val notificationKey = sbn.key
        if (!processedNotificationKeys.add(notificationKey)) {
            android.util.Log.i("MoneyManager", "GPay notification key $notificationKey already processed in this session")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ListenerEntryPoint::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Resolve or create default Account
                val allAccounts = entryPoint.accountDao().getAll()
                val account =
                    if (allAccounts.isEmpty()) {
                        val newId =
                            entryPoint.accountDao().insert(
                                AccountEntity(name = "Bank", parentId = null)
                            )
                        entryPoint.accountDao().getById(newId)
                    } else {
                        allAccounts.first()
                    } ?: return@launch

                // 2. Resolve or predict Category
                val allCategories = entryPoint.categoryDao().getAll()
                val finalCategoryId =
                    if (allCategories.isEmpty()) {
                        entryPoint.categoryDao().insert(
                            CategoryEntity(
                                name = "Other",
                                parentId = null,
                                type = TransactionType.EXPENSE,
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

                val reasonableNote =
                    when {
                        TransactionParser.isReasonableNote(parsed.note) -> parsed.note
                        TransactionParser.isReasonableNote(parsed.merchant) -> parsed.merchant
                        else -> null
                    }

                // 3. Prevent duplicate insertion if an identical transaction already exists
                val roundedAmount = parsed.amount.setScale(2, java.math.RoundingMode.HALF_UP)
                val existing = entryPoint.transactionDao().findMatching(roundedAmount, parsed.date, reasonableNote)
                if (existing != null) {
                    android.util.Log.i("MoneyManager", "Duplicate transaction found in DB (id=${existing.id}), skipping.")
                    return@launch
                }

                // 4. Save auto-detected expense with isApproved = false
                val addResult =
                    entryPoint.transactionRepository().addAutoExpense(
                        amount = parsed.amount,
                        date = parsed.date,
                        categoryId = category.id,
                        accountId = account.id,
                        note = reasonableNote,
                    )

                android.util.Log.i("MoneyManager", "addAutoExpense result: $addResult")

                // 5. Post notification
                if (addResult is AppResult.Success) {
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
                android.util.Log.e("MoneyManager", "Error processing GPay notification", e)
            }
        }
    }

    companion object {
        const val GPAY_INDIA_PACKAGE = "com.google.android.apps.nbu.paisa.user"
        const val GPAY_GLOBAL_PACKAGE = "com.google.android.apps.walletnfcrel"
    }
}

