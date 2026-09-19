package com.moneymanager.data.ingestion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moneymanager.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        init {
            createNotificationChannel()
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        "Detected Transactions",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "Notifications for auto-detected expenses with approve, edit, and delete actions"
                        enableVibration(true)
                    }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showTransactionNotification(
            transactionId: Long,
            amount: BigDecimal,
            merchant: String,
            categoryName: String,
            accountName: String,
            note: String?,
        ) {
            val notificationId = transactionId.toInt()

            // 1. Approve Intent
            val approveIntent =
                Intent(context, TransactionActionReceiver::class.java).apply {
                    action = ACTION_APPROVE
                    putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            val approvePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + 1,
                    approveIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            // 2. Delete Intent
            val deleteIntent =
                Intent(context, TransactionActionReceiver::class.java).apply {
                    action = ACTION_DELETE
                    putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            val deletePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + 2,
                    deleteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            // 3. Edit Intent (opens MainActivity to EntryScreen)
            val editIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_EDIT_TRANSACTION_ID, transactionId)
                }
            val editPendingIntent =
                PendingIntent.getActivity(
                    context,
                    notificationId * 10 + 3,
                    editIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val body =
                buildString {
                    append("$categoryName · $accountName")
                    if (!note.isNullOrBlank()) {
                        append(" · $note")
                    }
                }

            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle("New Expense: ₹$amount at $merchant")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(editPendingIntent)
                    .addAction(0, "Approve", approvePendingIntent)
                    .addAction(0, "Edit", editPendingIntent)
                    .addAction(0, "Delete", deletePendingIntent)
                    .build()

            try {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            } catch (_: SecurityException) {
                // If notification permission is not granted on Android 13+
            }
        }

        fun cancelNotification(notificationId: Int) {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (_: SecurityException) {
            }
        }

        companion object {
            const val CHANNEL_ID = "money_manager_transactions"
            const val ACTION_APPROVE = "com.moneymanager.ACTION_APPROVE_TRANSACTION"
            const val ACTION_DELETE = "com.moneymanager.ACTION_DELETE_TRANSACTION"
            const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
            const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
            const val EXTRA_EDIT_TRANSACTION_ID = "extra_edit_transaction_id"
        }
    }

