package com.moneymanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.model.AppSettings
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The theme inputs the root needs to pick colors before drawing. */
data class ThemePrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val seedColorArgb: Int = AppSettings.DEFAULT_SEED_COLOR,
)

/** Exposes just the theme preferences so the root can pick colors before drawing. */
@HiltViewModel
class MoneyManagerRootViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val themePrefs: StateFlow<ThemePrefs> =
            settingsRepository.observe()
                .map {
                    ThemePrefs(
                        themeMode = it.themeMode,
                        dynamicColor = it.dynamicColor,
                        seedColorArgb = it.seedColorArgb,
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ThemePrefs(),
                )
    }
