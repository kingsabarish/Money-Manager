package com.moneymanager.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations (Navigation-Compose reads these via the
// kotlinx.serialization plugin). Data objects for now; screens that take
// arguments (e.g. edit an expense by id) become @Serializable data classes.

@Serializable
data object Home

@Serializable
data object Settings
