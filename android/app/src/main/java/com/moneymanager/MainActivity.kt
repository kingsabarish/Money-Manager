package com.moneymanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.moneymanager.ui.MoneyManagerRoot
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host for the Compose UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val editTransactionId = androidx.compose.runtime.mutableStateOf<Long?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Permissions granted or denied by user
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val editId = intent.getLongExtra(
            com.moneymanager.data.ingestion.TransactionNotificationManager.EXTRA_EDIT_TRANSACTION_ID,
            -1L,
        ).takeIf { it != -1L }
        handleIntent(intent)

        requestPermissionsIfNeeded()

        setContent {
            MoneyManagerRoot(initialEditTransactionId = editId)
            MoneyManagerRoot(
                initialEditTransactionId = editTransactionId.value,
                onEditTransactionHandled = { editTransactionId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val editId =
            intent?.getLongExtra(
                com.moneymanager.data.ingestion.TransactionNotificationManager.EXTRA_EDIT_TRANSACTION_ID,
                -1L,
            )?.takeIf { it != -1L }
        if (editId != null) {
            editTransactionId.value = editId
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

