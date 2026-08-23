package com.moneymanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = BrandGreen,
        secondary = BrandTeal,
        tertiary = BrandAmber,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = BrandGreenLight,
        secondary = BrandTealLight,
        tertiary = BrandAmberLight,
    )

/**
 * App-wide Material 3 theme.
 *
 * Dynamic (wallpaper-based) color is only available on Android 12 (API 31, "S")
 * and up; on our minSdk 26 baseline we must fall back to the static palette, so
 * the [Build.VERSION.SDK_INT] guard below is mandatory — calling the dynamic*
 * builders on older APIs would crash.
 */
@Composable
fun MoneyManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
