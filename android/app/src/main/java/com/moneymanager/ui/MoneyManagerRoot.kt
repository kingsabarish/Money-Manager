package com.moneymanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.ui.navigation.MoneyManagerNavHost
import com.moneymanager.ui.theme.MoneyManagerTheme

/**
 * Top-level composable: applies the theme (honoring the user's preferences) and
 * hosts the navigation graph. Named "Root" to avoid clashing with the
 * [com.moneymanager.MoneyManagerApp] Application class.
 */
@Composable
fun MoneyManagerRoot(viewModel: MoneyManagerRootViewModel = hiltViewModel()) {
    val prefs by viewModel.themePrefs.collectAsStateWithLifecycle()

    val darkTheme =
        when (prefs.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    MoneyManagerTheme(appTheme = prefs.appTheme, darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            MoneyManagerNavHost()
        }
    }
}
