package com.volxsy.vastypr.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volxsy.vastypr.data.prefs.UserPrefs
import com.volxsy.vastypr.ml.models.DownloadableModel
import com.volxsy.vastypr.ml.models.InpaintBackend
import com.volxsy.vastypr.ml.models.MODEL_CATALOG
import com.volxsy.vastypr.ml.models.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Skill: android-state-management + android-coroutines-flow
// URL per model (override user; kosong = pakai defaultUrl katalog).
data class ModelUiState(
    val backend: String = InpaintBackend.TELEA.id,
    val urls: Map<String, String> = emptyMap(), // modelId -> override
    val remoteMb: Map<String, Double?> = emptyMap(),
    val busy: Boolean = false,
)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val manager: ModelManager,
    private val prefs: UserPrefs,
) : ViewModel() {

    private val _ui = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _ui.asStateFlow()
    // Kompat:
    val lama = manager.lama
    val migan = manager.migan
    val bubble = manager.bubble
    val ppocr = manager.ppocr

    fun statusFlow(modelId: String) = manager.statusFlow(modelId)

    private val _msg = MutableSharedFlow<String>(replay = 0)
    val messages: SharedFlow<String> = _msg.asSharedFlow()

    init {
        viewModelScope.launch {
            manager.refresh()
            _ui.update {
                it.copy(
                    backend = prefs.inpaintBackend.first(),
                    urls = mapOf(
                        "lama" to prefs.lamaUrl().first(),
                        "migan" to prefs.miganUrl().first(),
                        "bubble" to prefs.bubbleUrl().first(),
                        "ppocr" to prefs.ppocrUrl().first(),
                    ),
                )
            }
            refreshRemoteSizes()
        }
    }

    fun onBackend(v: String) {
        _ui.update { it.copy(backend = v) }
        viewModelScope.launch { prefs.setInpaintBackend(v) }
    }

    fun urlFor(m: DownloadableModel): String =
        _ui.value.urls[m.modelId].orEmpty().ifBlank { m.defaultUrl }

    fun isOverridden(m: DownloadableModel): Boolean =
        _ui.value.urls[m.modelId].orEmpty().isNotBlank()

    fun onUrl(m: DownloadableModel, v: String) {
        _ui.update {
            it.copy(
                urls = it.urls + (m.modelId to v),
                remoteMb = it.remoteMb + (m.modelId to null),
            )
        }
        viewModelScope.launch {
            when (m.modelId) {
                "lama" -> prefs.setLamaUrl(v.trim())
                "migan" -> prefs.setMiganUrl(v.trim())
                "bubble" -> prefs.setBubbleUrl(v.trim())
                "ppocr" -> prefs.setPpocrUrl(v.trim())
            }
            refreshRemoteSizes()
        }
    }

    fun resetUrl(m: DownloadableModel) = onUrl(m, "")

    private suspend fun refreshRemoteSizes() {
        val urls = _ui.value.urls
        val out = mutableMapOf<String, Double?>()
        MODEL_CATALOG.forEach { m ->
            val eff = urls[m.modelId].orEmpty().ifBlank { m.defaultUrl }
            out[m.modelId] = if (eff.isNotBlank()) manager.remoteSizeMb(eff) else null
        }
        _ui.update { it.copy(remoteMb = out) }
    }

    fun download(m: DownloadableModel) {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            manager.download(m) { ok, msg ->
                _ui.update { it.copy(busy = false) }
                viewModelScope.launch { _msg.emit(msg) }
            }
        }
    }

    fun cancel() = manager.cancel()

    fun delete(m: DownloadableModel) {
        viewModelScope.launch {
            manager.delete(m)
            _msg.emit("${m.label} dihapus" + if (m.backend.id != "telea") " — backend kembali ke Telea bila sedang aktif" else "")
            _ui.update { it.copy(backend = prefs.inpaintBackend.first()) }
        }
    }
}
