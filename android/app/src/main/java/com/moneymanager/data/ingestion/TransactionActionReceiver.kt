package com.moneymanager.data.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moneymanager.data.categorization.CategorizationEngine
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionActionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun transactionRepository(): TransactionRepository

        fun categorizationEngine(): CategorizationEngine

        fun notificationManager(): TransactionNotificationManager
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val transactionId = intent.getLongExtra(TransactionNotificationManager.EXTRA_TRANSACTION_ID, -1L)
        val notificationId = intent.getIntExtra(TransactionNotificationManager.EXTRA_NOTIFICATION_ID, -1)
        if (transactionId == -1L) return

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    TransactionNotificationManager.ACTION_APPROVE -> {
                        val txnResult = entryPoint.transactionRepository().getById(transactionId)
                        if (txnResult is AppResult.Success) {
                            val txn = txnResult.data
                            entryPoint.transactionRepository().approve(transactionId)
                            // Reinforce ML weights with confirmed category
                            entryPoint.categorizationEngine().train(
                                merchant = txn.note ?: "",
                                note = txn.note,
                                amount = txn.amount,
                                assignedCategoryId = txn.categoryId,
                            )
                        }
                    }
                    TransactionNotificationManager.ACTION_DELETE -> {
                        entryPoint.transactionRepository().delete(transactionId)
                    }
                }
            } finally {
                if (notificationId != -1) {
                    entryPoint.notificationManager().cancelNotification(notificationId)
                }
                pendingResult.finish()
            }
        }
    }
}

