package com.moneymanager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneymanager.ui.navigation.MoneyManagerNavHost
import com.moneymanager.ui.theme.MoneyManagerTheme

/**
 * Top-level composable: applies the theme and hosts the navigation graph.
 * Named "Root" to avoid clashing with the [com.moneymanager.MoneyManagerApp]
 * Application class.
 */
@Composable
fun MoneyManagerRoot() {
    MoneyManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            MoneyManagerNavHost()
        }
    }
}
