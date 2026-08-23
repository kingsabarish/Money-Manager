package com.moneymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the Compose UI.
 *
 * Structure stub — the navigation graph and theme will be wired in here once
 * the UI layer exists. No logic yet.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // TODO: apply MoneyManager theme and host the navigation graph.
        }
    }
}
