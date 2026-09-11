package com.volxsy.vastypr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volxsy.vastypr.data.security.ApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val agnesKey: String = "",
    val geminiKey: String = "",
    val sumopodKey: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keys: ApiKeyStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                agnesKey = keys.getAgnes(),
                geminiKey = keys.getGemini(),
                sumopodKey = keys.getSumopod(),
            )
        }
    }

    fun onAgnesChange(v: String) {
        _uiState.update { it.copy(agnesKey = v) }
        viewModelScope.launch { keys.setAgnes(v) }
    }
    fun onGeminiChange(v: String) {
        _uiState.update { it.copy(geminiKey = v) }
        viewModelScope.launch { keys.setGemini(v) }
    }
    fun onSumopodChange(v: String) {
        _uiState.update { it.copy(sumopodKey = v) }
        viewModelScope.launch { keys.setSumopod(v) }
    }
}
