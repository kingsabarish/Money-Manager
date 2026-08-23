package com.moneymanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.moneymanager.domain.model.AppTheme

private val LightColorScheme =
    lightColorScheme(
        primary = md_light_primary,
        onPrimary = md_light_onPrimary,
        primaryContainer = md_light_primaryContainer,
        onPrimaryContainer = md_light_onPrimaryContainer,
        secondary = md_light_secondary,
        onSecondary = md_light_onSecondary,
        secondaryContainer = md_light_secondaryContainer,
        onSecondaryContainer = md_light_onSecondaryContainer,
        tertiary = md_light_tertiary,
        onTertiary = md_light_onTertiary,
        tertiaryContainer = md_light_tertiaryContainer,
        onTertiaryContainer = md_light_onTertiaryContainer,
        error = md_light_error,
        onError = md_light_onError,
        errorContainer = md_light_errorContainer,
        onErrorContainer = md_light_onErrorContainer,
        background = md_light_background,
        onBackground = md_light_onBackground,
        surface = md_light_surface,
        onSurface = md_light_onSurface,
        surfaceVariant = md_light_surfaceVariant,
        onSurfaceVariant = md_light_onSurfaceVariant,
        outline = md_light_outline,
        outlineVariant = md_light_outlineVariant,
        surfaceContainerLowest = md_light_surfaceContainerLowest,
        surfaceContainerLow = md_light_surfaceContainerLow,
        surfaceContainer = md_light_surfaceContainer,
        surfaceContainerHigh = md_light_surfaceContainerHigh,
        surfaceContainerHighest = md_light_surfaceContainerHighest,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = md_dark_primary,
        onPrimary = md_dark_onPrimary,
        primaryContainer = md_dark_primaryContainer,
        onPrimaryContainer = md_dark_onPrimaryContainer,
        secondary = md_dark_secondary,
        onSecondary = md_dark_onSecondary,
        secondaryContainer = md_dark_secondaryContainer,
        onSecondaryContainer = md_dark_onSecondaryContainer,
        tertiary = md_dark_tertiary,
        onTertiary = md_dark_onTertiary,
        tertiaryContainer = md_dark_tertiaryContainer,
        onTertiaryContainer = md_dark_onTertiaryContainer,
        error = md_dark_error,
        onError = md_dark_onError,
        errorContainer = md_dark_errorContainer,
        onErrorContainer = md_dark_onErrorContainer,
        background = md_dark_background,
        onBackground = md_dark_onBackground,
        surface = md_dark_surface,
        onSurface = md_dark_onSurface,
        surfaceVariant = md_dark_surfaceVariant,
        onSurfaceVariant = md_dark_onSurfaceVariant,
        outline = md_dark_outline,
        outlineVariant = md_dark_outlineVariant,
        surfaceContainerLowest = md_dark_surfaceContainerLowest,
        surfaceContainerLow = md_dark_surfaceContainerLow,
        surfaceContainer = md_dark_surfaceContainer,
        surfaceContainerHigh = md_dark_surfaceContainerHigh,
        surfaceContainerHighest = md_dark_surfaceContainerHighest,
    )

/**
 * Overlay only the three accent families (primary / secondary / tertiary + their
 * containers) onto a base scheme, keeping its neutral surfaces and error roles.
 * Lets the alternative palettes share the green-tinted neutrals for a consistent
 * look while swapping just the accent hue.
 */
private fun ColorScheme.withAccents(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
): ColorScheme =
    copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
    )

private val BlueLightScheme =
    LightColorScheme.withAccents(
        blue_light_primary, blue_light_onPrimary,
        blue_light_primaryContainer, blue_light_onPrimaryContainer,
        blue_light_secondary, blue_light_onSecondary,
        blue_light_secondaryContainer, blue_light_onSecondaryContainer,
        blue_light_tertiary, blue_light_onTertiary,
        blue_light_tertiaryContainer, blue_light_onTertiaryContainer,
    )

private val BlueDarkScheme =
    DarkColorScheme.withAccents(
        blue_dark_primary, blue_dark_onPrimary,
        blue_dark_primaryContainer, blue_dark_onPrimaryContainer,
        blue_dark_secondary, blue_dark_onSecondary,
        blue_dark_secondaryContainer, blue_dark_onSecondaryContainer,
        blue_dark_tertiary, blue_dark_onTertiary,
        blue_dark_tertiaryContainer, blue_dark_onTertiaryContainer,
    )

private val PurpleLightScheme =
    LightColorScheme.withAccents(
        purple_light_primary, purple_light_onPrimary,
        purple_light_primaryContainer, purple_light_onPrimaryContainer,
        purple_light_secondary, purple_light_onSecondary,
        purple_light_secondaryContainer, purple_light_onSecondaryContainer,
        purple_light_tertiary, purple_light_onTertiary,
        purple_light_tertiaryContainer, purple_light_onTertiaryContainer,
    )

private val PurpleDarkScheme =
    DarkColorScheme.withAccents(
        purple_dark_primary, purple_dark_onPrimary,
        purple_dark_primaryContainer, purple_dark_onPrimaryContainer,
        purple_dark_secondary, purple_dark_onSecondary,
        purple_dark_secondaryContainer, purple_dark_onSecondaryContainer,
        purple_dark_tertiary, purple_dark_onTertiary,
        purple_dark_tertiaryContainer, purple_dark_onTertiaryContainer,
    )

private val OrangeLightScheme =
    LightColorScheme.withAccents(
        orange_light_primary, orange_light_onPrimary,
        orange_light_primaryContainer, orange_light_onPrimaryContainer,
        orange_light_secondary, orange_light_onSecondary,
        orange_light_secondaryContainer, orange_light_onSecondaryContainer,
        orange_light_tertiary, orange_light_onTertiary,
        orange_light_tertiaryContainer, orange_light_onTertiaryContainer,
    )

private val OrangeDarkScheme =
    DarkColorScheme.withAccents(
        orange_dark_primary, orange_dark_onPrimary,
        orange_dark_primaryContainer, orange_dark_onPrimaryContainer,
        orange_dark_secondary, orange_dark_onSecondary,
        orange_dark_secondaryContainer, orange_dark_onSecondaryContainer,
        orange_dark_tertiary, orange_dark_onTertiary,
        orange_dark_tertiaryContainer, orange_dark_onTertiaryContainer,
    )

/** Static scheme for a fixed [AppTheme] palette (everything except [AppTheme.DYNAMIC]). */
private fun staticScheme(appTheme: AppTheme, darkTheme: Boolean): ColorScheme =
    when (appTheme) {
        AppTheme.BLUE -> if (darkTheme) BlueDarkScheme else BlueLightScheme
        AppTheme.PURPLE -> if (darkTheme) PurpleDarkScheme else PurpleLightScheme
        AppTheme.ORANGE -> if (darkTheme) OrangeDarkScheme else OrangeLightScheme
        // GREEN is the brand default; DYNAMIC falls back here below API 31.
        AppTheme.GREEN, AppTheme.DYNAMIC -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

/**
 * App-wide Material 3 theme.
 *
 * [appTheme] selects the color palette. [AppTheme.DYNAMIC] matches the device
 * wallpaper (Material You) and is only available on Android 12 (API 31, "S") and
 * up, so on our minSdk 26 baseline the [Build.VERSION.SDK_INT] guard below is
 * mandatory — calling the dynamic* builders on older APIs crashes. Below API 31
 * the DYNAMIC choice falls back to the static green brand palette.
 */
@Composable
fun MoneyManagerTheme(
    appTheme: AppTheme = AppTheme.GREEN,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (appTheme == AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            staticScheme(appTheme, darkTheme)
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
