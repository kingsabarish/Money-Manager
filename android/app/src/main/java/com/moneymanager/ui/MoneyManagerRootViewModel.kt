package com.moneymanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.model.ThemeMode
import com.moneymanager.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes just the theme preference so the root can pick light/dark before drawing. */
@HiltViewModel
class MoneyManagerRootViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            settingsRepository.observe()
                .map { it.themeMode }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ThemeMode.SYSTEM,
                )
    }
