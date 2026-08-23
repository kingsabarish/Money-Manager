package com.moneymanager.ui.feature.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moneymanager.ui.theme.MoneyManagerTheme

/**
 * Landing screen. Placeholder for the expense list that arrives in a later slice;
 * for now it establishes the themed Scaffold + top bar and the route to Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money Manager") },
                actions = {
                    TextButton(onClick = onNavigateToSettings) { Text("Settings") }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No expenses yet",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MoneyManagerTheme {
        HomeScreen(onNavigateToSettings = {})
    }
}
