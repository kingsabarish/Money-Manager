package com.moneymanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme

/**
 * App-wide Material 3 theme. The entire palette is generated from a single
 * [seedColor] (the user's chosen accent) using Google's material-color-utilities
 * tonal algorithm, so any RGB color yields a complete, harmonious scheme.
 *
 * When [dynamicColor] is on and the device runs Android 12+ (API 31, "S"), the
 * palette instead matches the wallpaper (Material You). The [Build.VERSION.SDK_INT]
 * guard is mandatory on our minSdk 26 baseline — the dynamic* builders crash on
 * older APIs — so below API 31 we fall back to the seed-generated scheme.
 */
@Composable
fun MoneyManagerTheme(
    seedColor: Color = BrandGreen,
    dynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            rememberDynamicColorScheme(seedColor = seedColor, isDark = darkTheme)
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
