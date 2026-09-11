package com.volxsy.vastypr.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volxsy.vastypr.data.local.ProjectDao
import com.volxsy.vastypr.data.local.ProjectEntity
import com.volxsy.vastypr.editor.io.ImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Skill: android-state-management — 1 immutable UiState + 1 event flow.
// FLUID rev2: tambah importUri/delete agar Home bisa import beneran (sebelumnya
// FAB hanya emit event tanpa handler -> tombol mati) + error first-class.
data class HomeUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface HomeEvent {
    data object RequestImportPicker : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dao: ProjectDao,
    private val importManager: ImportManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(replay = 0)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching {
                dao.observeAll().collect { list ->
                    _uiState.update { it.copy(projects = list, isLoading = false, errorMessage = null) }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = (e.message ?: "Load gagal").take(200)) }
            }
        }
    }

    fun onImportRequest() {
        viewModelScope.launch { _events.emit(HomeEvent.RequestImportPicker) }
    }

    fun importUri(uri: Uri, displayName: String) {
        viewModelScope.launch {
            try {
                importManager.registerImported(uri, displayName)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = ("Import gagal: ${(e.message ?: "unknown")}").take(250)) }
            }
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            runCatching { dao.delete(id) }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = ("Hapus gagal: ${(e.message ?: "unknown")}").take(200)) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
