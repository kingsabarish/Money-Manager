package com.moneymanager.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneymanager.R
import com.moneymanager.ui.feature.entry.EntryScreen
import com.moneymanager.ui.feature.manage.ManageScreen
import com.moneymanager.ui.feature.settings.SettingsScreen
import com.moneymanager.ui.feature.stats.StatsScreen
import com.moneymanager.ui.feature.transactions.HomeScreen

/** A destination reachable from the bottom navigation bar. */
private data class BottomTab(
    val route: Any,
    val label: String,
    @param:DrawableRes val icon: Int,
)

private val bottomTabs =
    listOf(
        BottomTab(Home, "Transactions", R.drawable.ic_tab_transactions),
        BottomTab(Stats, "Stats", R.drawable.ic_tab_stats),
        BottomTab(Settings, "Settings", R.drawable.ic_tab_settings),
    )

/**
 * Single navigation graph. Home, Stats, and Settings are the three bottom-bar
 * tabs; Entry and Manage are pushed on top full-screen (no bottom bar).
 */
@Composable
fun MoneyManagerNavHost(
    initialEditTransactionId: Long? = null,
    onEditTransactionHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar =
        currentDestination?.let { dest -> bottomTabs.any { dest.hasRoute(it.route::class) } } ?: true

    androidx.compose.runtime.LaunchedEffect(initialEditTransactionId) {
        if (initialEditTransactionId != null) {
            navController.navigate(Entry(transactionId = initialEditTransactionId))
            onEditTransactionHandled()
        }
    }

    val navigateBackOrHome = {
        if (!navController.popBackStack()) {
            navController.navigate(Home) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Keep a single copy per tab and preserve each tab's state.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(tab.icon),
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier =
                Modifier.padding(
                    bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp,
                ),
        ) {
            composable<Home> {
                HomeScreen(
                    onAddExpense = { navController.navigate(Entry()) },
                    onEditExpense = { id -> navController.navigate(Entry(id)) },
                    onNavigateToUnapproved = { navController.navigate(Unapproved) },
                )
            }
            composable<Stats> {
                StatsScreen()
            }
            composable<Settings> {
                SettingsScreen(onNavigateToManage = { navController.navigate(Manage) })
            }
            composable<Entry> {
                EntryScreen(
                    onNavigateBack = navigateBackOrHome,
                    onSaved = navigateBackOrHome,
                )
            }
            composable<Manage> {
                ManageScreen(onNavigateBack = navigateBackOrHome)
            }
            composable<Unapproved> {
                com.moneymanager.ui.feature.unapproved.UnapprovedTransactionsScreen(
                    onBack = navigateBackOrHome,
                    onEditTransaction = { id -> navController.navigate(Entry(id)) },
                )
            }
        }
    }
}
