package com.moneymanager.data.ingestion

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneymanager.data.account.AccountResolutionEngine
import com.moneymanager.data.categorization.CategorizationEngine
import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.CategoryDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.model.TransactionType
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections

class TransactionNotificationListenerService : NotificationListenerService() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun transactionRepository(): TransactionRepository

        fun categorizationEngine(): CategorizationEngine

        fun accountResolutionEngine(): AccountResolutionEngine

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

    private data class ExtractedMessage(
        val text: String,
        val timestampEpochMs: Long,
        val title: String,
    )

    private fun extractMessagesFromNotification(sbn: StatusBarNotification): List<ExtractedMessage> {
        val extras = sbn.notification.extras ?: return emptyList()
        val defaultTitle =
            (extras.getCharSequence(Notification.EXTRA_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString() ?: ""
        val postTime = sbn.postTime

        val extractedMap = LinkedHashMap<String, ExtractedMessage>()

        fun addMsg(text: CharSequence?, time: Long?, sender: CharSequence?) {
            val textStr = text?.toString()?.trim() ?: return
            if (textStr.isBlank()) return
            val timeMs = if (time != null && time > 0) time else postTime
            val titleStr = sender?.toString()?.takeIf { it.isNotBlank() } ?: defaultTitle
            if (!extractedMap.containsKey(textStr)) {
                extractedMap[textStr] = ExtractedMessage(
                    text = textStr,
                    timestampEpochMs = timeMs,
                    title = titleStr
                )
            }
        }

        // 1. AndroidX NotificationCompat.MessagingStyle (both new and historic messages)
        try {
            val messagingStyle =
                androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
            if (messagingStyle != null) {
                for (m in messagingStyle.messages) {
                    addMsg(m.text, m.timestamp, m.person?.name)
                }
                for (m in messagingStyle.historicMessages) {
                    addMsg(m.text, m.timestamp, m.person?.name)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MoneyManager", "Error extracting MessagingStyle", e)
        }

        // 2. Direct inspection of extras "android.messages" and "android.messages.historic"
        for (key in listOf("android.messages", "android.messages.historic")) {
            val raw = extras.get(key)
            val bundleList: List<Bundle> = when (raw) {
                is Array<*> -> raw.filterIsInstance<Bundle>()
                is Iterable<*> -> raw.filterIsInstance<Bundle>()
                else -> emptyList()
            }
            for (b in bundleList) {
                val text = b.getCharSequence("text")
                val time = b.getLong("time", postTime)
                val sender = b.getCharSequence("sender")
                addMsg(text, time, sender)
            }
        }

        // 3. InboxStyle lines (e.g. Notification.EXTRA_TEXT_LINES / "android.textLines")
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!textLines.isNullOrEmpty()) {
            for (line in textLines) {
                addMsg(line, postTime, defaultTitle)
            }
        }

        // 4. Fallback: single text or bigText if nothing found yet
        if (extractedMap.isEmpty()) {
            val singleText =
                (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            addMsg(singleText, postTime, defaultTitle)
        }

        return extractedMap.values.toList()
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        // Check if package is in supported list
        if (!SUPPORTED_PACKAGES.contains(pkg)) return

        val extractedMessages = extractMessagesFromNotification(sbn)
        if (extractedMessages.isEmpty()) return

        android.util.Log.i("MoneyManager", "Extracted ${extractedMessages.size} messages from $pkg")

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ListenerEntryPoint::class.java)

        for ((idx, item) in extractedMessages.withIndex()) {
            val parsed = if (pkg == GPAY_INDIA_PACKAGE || pkg == GPAY_GLOBAL_PACKAGE) {
                val gpayResult = TransactionParser.parseGPayNotification(
                    title = item.title,
                    text = item.text,
                    timestampEpochMs = item.timestampEpochMs,
                )
                if (gpayResult is ParsedTransaction.Expense) gpayResult
                else TransactionParser.parseSms(item.text, item.timestampEpochMs)
            } else {
                val smsResult = TransactionParser.parseSms(item.text, item.timestampEpochMs)
                if (smsResult is ParsedTransaction.Expense) smsResult
                else TransactionParser.parseGPayNotification(
                    title = item.title,
                    text = item.text,
                    timestampEpochMs = item.timestampEpochMs,
                )
            }

            android.util.Log.i("MoneyManager", "Msg [$idx]: parsed result = $parsed for text: ${item.text}")

            if (parsed !is ParsedTransaction.Expense) continue

            val dedupeKey = "${parsed.amount}:${parsed.date}:${parsed.merchant}:${parsed.note}"
            if (!processedNotificationKeys.add(dedupeKey)) {
                android.util.Log.i("MoneyManager", "Notification transaction $dedupeKey already processed in this session")
                continue
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Resolve Account via dynamic AccountResolutionEngine
                    val account = entryPoint.accountResolutionEngine().resolveAccount(
                        rawText = item.text,
                        accountRef = parsed.accountRef,
                        merchant = parsed.merchant,
                        amount = parsed.amount,
                    )
                    android.util.Log.i("MoneyManager", "Resolved account: ${account.name} (id=${account.id})")

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
                    android.util.Log.i("MoneyManager", "Resolved category: ${category.name} (id=${category.id})")

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
                            merchant = parsed.merchant,
                        )

                    android.util.Log.i("MoneyManager", "Notification addAutoExpense result: $addResult")

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
                    android.util.Log.e("MoneyManager", "Error processing notification message", e)
                }
            }
        }
    }

    companion object {
        const val GPAY_INDIA_PACKAGE = "com.google.android.apps.nbu.paisa.user"
        const val GPAY_GLOBAL_PACKAGE = "com.google.android.apps.walletnfcrel"

        val SUPPORTED_PACKAGES = setOf(
            GPAY_INDIA_PACKAGE,
            GPAY_GLOBAL_PACKAGE,
            // SMS apps
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.oneplus.mms",
            "com.oplus.mms",
            "com.android.mms",
            "com.motorola.mms",
            // UPI & Bank apps
            "net.one97.paytm",
            "com.phonepe.app",
            "in.org.npci.upiapp",
            "com.hdfcbank.payzapp",
        )
    }
}
