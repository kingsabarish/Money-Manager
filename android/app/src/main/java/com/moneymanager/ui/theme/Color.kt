package com.moneymanager.ui.theme

import androidx.compose.ui.graphics.Color

/** Brand green — the default accent seed the Material 3 palette is generated from. */
val BrandGreen = Color(0xFF2E6B4F)

/**
 * Quick-pick accent seeds offered in Settings above the full color picker.
 * Each is just a seed; the whole scheme is derived from it.
 */
val ThemePresetSeeds: List<Color> =
    listOf(
        BrandGreen,
        Color(0xFF265DA8), // blue
        Color(0xFF6750A4), // purple
        Color(0xFFB3261E), // red
        Color(0xFF8F4C00), // orange
        Color(0xFF00696E), // teal
        Color(0xFFB5006B), // magenta
        Color(0xFF4B5D00), // olive
    )

/**
 * Distinct, evenly-spread colors for the stats chart slices/legend. Chosen for
 * legibility on both light and dark surfaces; cycled if there are more slices.
 */
val ChartPalette: List<Color> =
    listOf(
        Color(0xFFEF6C6C),
        Color(0xFFF3A24B),
        Color(0xFFF6D046),
        Color(0xFF8FCF52),
        Color(0xFF4FB06E),
        Color(0xFF4FC3C7),
        Color(0xFF5B9BF0),
        Color(0xFF9B7DE0),
        Color(0xFFD887D6),
        Color(0xFFB0885E),
        Color(0xFF7E8CA0),
        Color(0xFFE0739E),
    )
