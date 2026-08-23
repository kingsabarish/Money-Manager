package com.moneymanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moneymanager.ui.feature.settings.SettingsScreen
import com.moneymanager.ui.feature.transactions.HomeScreen

/**
 * Single navigation graph for the app. Home is the start destination (the future
 * expense list); Settings hosts backup/preferences.
 */
@Composable
fun MoneyManagerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(onNavigateToSettings = { navController.navigate(Settings) })
        }
        composable<Settings> {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
