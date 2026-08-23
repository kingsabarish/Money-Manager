package com.moneymanager.domain.model

/**
 * User's color palette preference, independent of light/dark ([ThemeMode]).
 *
 * [DYNAMIC] matches the device wallpaper (Material You) and is only honored on
 * Android 12+ (API 31); on older devices it falls back to the [GREEN] brand
 * palette. [GREEN] is the default. The rest are fixed accent schemes.
 */
enum class AppTheme {
    DYNAMIC,
    GREEN,
    BLUE,
    PURPLE,
    ORANGE,
}
