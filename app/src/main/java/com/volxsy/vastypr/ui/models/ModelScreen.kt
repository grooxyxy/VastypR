package com.volxsy.vastypr.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volxsy.vastypr.ml.models.DownloadableModel
import com.volxsy.vastypr.ml.models.MODEL_CATALOG
import com.volxsy.vastypr.ml.models.ModelManager

// Skill: android-mobile-frontend-design + android-state-management
// Semua model MANUAL per tombol (tidak ada download otomatis):
// - Bubble: dibundle APK bila build menyertakan (task bundleBubbleModel),
//   fallback file manual / tombol Download.
// - PP-OCR / MiGAN / LaMa: TIDAK dibundle — hanya via Download eksplisit
//   (URL default resmi, bisa diganti link Release sendiri).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(onBack: () -> Unit, vm: ModelViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Models") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Bubble dibundle di APK bila build menyertakannya — lainnya TIDAK dibundle. " +
                    "Semua model hanya didownload bila kamu menekan Download.",
                style = MaterialTheme.typography.bodySmall,
            )
            SectionTitle("Deteksi bubble")
            MODEL_CATALOG.filter { it.modelId == "bubble" }.forEach { m ->
                ModelCardHost(vm, m, state)
            }
            SectionTitle("Mask teks (otomatis)")
            MODEL_CATALOG.filter { it.modelId == "ppocr" }.forEach { m ->
                ModelCardHost(vm, m, state)
            }
            SectionTitle("Backend inpaint")
            BackendRow("Telea (built-in)", "Cepat, ringan, tanpa file — default.", state.backend == "telea") {
                vm.onBackend("telea")
            }
            MODEL_CATALOG.filter { it.modelId == "lama" || it.modelId == "migan" }.forEach { m ->
                ModelCardHost(vm, m, state)
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ModelCardHost(vm: ModelViewModel, m: DownloadableModel, state: ModelUiState) {
    val status by vm.statusFlow(m.modelId).collectAsStateWithLifecycle(initialValue = ModelManager.Status())
    val isInpaint = m.modelId == "lama" || m.modelId == "migan"
    ModelCard(
        m = m,
        status = status,
        url = vm.urlFor(m),
        isDefaultUrl = !vm.isOverridden(m) && m.defaultUrl.isNotBlank(),
        remoteMb = state.remoteMb[m.modelId],
        active = isInpaint && state.backend == m.backend.id,
        showBackend = isInpaint,
        busy = state.busy,
        onUrl = { vm.onUrl(m, it) },
        onResetUrl = { vm.resetUrl(m) },
        onActivate = { vm.onBackend(m.backend.id) },
        onDownload = { vm.download(m) },
        onCancel = vm::cancel,
        onDelete = { vm.delete(m) },
    )
}

@Composable
private fun BackendRow(title: String, desc: String, active: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = active, onClick = onClick)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title); Text(desc, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModelCard(
    m: DownloadableModel,
    status: ModelManager.Status,
    url: String,
    isDefaultUrl: Boolean,
    remoteMb: Double?,
    active: Boolean,
    showBackend: Boolean,
    busy: Boolean,
    onUrl: (String) -> Unit,
    onResetUrl: () -> Unit,
    onActivate: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBackend) {
                    RadioButton(selected = active, onClick = onActivate, enabled = status.installed)
                }
                Column(Modifier.weight(1f).padding(start = if (showBackend) 8.dp else 0.dp)) {
                    Text("${m.label}  (estimasi ${m.estimateMb})")
                    Text(m.description, style = MaterialTheme.typography.bodySmall)
                    if (m.bundled) {
                        Text(
                            "Bundled: ikut APK bila build menyertakan file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        when {
                            status.downloading -> "Downloading… ${(status.progress * 100).toInt().takeIf { status.progress >= 0 }?.let { "$it%" } ?: ""}"
                            status.installed -> if (status.sizeMb > 0) {
                                "Terinstall • ${"%.1f".format(status.sizeMb)} MB"
                            } else {
                                "Tersedia (bundled di APK)"
                            }
                            else -> "Belum didownload"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    status.error?.let {
                        Text("Error: $it", color = MaterialTheme.colorScheme.error)
                    }
                    remoteMb?.let { Text("Ukuran server: ${"%.1f".format(it)} MB") }
                }
            }
            if (status.downloading && status.progress >= 0) {
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = url, onValueChange = onUrl,
                label = { Text("Link ${m.fileName}") },
                placeholder = { Text("tautan resmi (bisa diganti)") },
                supportingText = {
                    Text(
                        if (m.defaultUrl.isBlank()) "Wajib isi manual (punyamu / Release sendiri)"
                        else if (isDefaultUrl) "Default resmi — kosongkan override? Sudah default."
                        else "Override milikmu (Reset untuk kembali default)"
                    )
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.downloading) {
                    OutlinedButton(onClick = onCancel) { Text("Batal") }
                } else if (status.installed && status.sizeMb > 0) {
                    if (showBackend) {
                        Button(onClick = onActivate, enabled = !active) { Text(if (active) "Aktif" else "Pakai") }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus model")
                    }
                } else if (status.installed) {
                    // Bundled tanpa file terukur — tidak ada yang bisa dihapus/diaktifkan.
                    if (showBackend) {
                        Button(onClick = onActivate, enabled = !active) { Text(if (active) "Aktif" else "Pakai") }
                    } else {
                        Text("Siap dipakai.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Button(onClick = onDownload, enabled = !busy && url.isNotBlank()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Text("  Download")
                    }
                }
                if (!isDefaultUrl && m.defaultUrl.isNotBlank()) {
                    TextButton(onClick = onResetUrl) { Text("Reset URL") }
                }
            }
        }
    }
}
