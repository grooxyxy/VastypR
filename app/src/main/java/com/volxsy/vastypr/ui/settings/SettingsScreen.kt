package com.volxsy.vastypr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Skill: android-security-best-practices + android-state-management.
// FLUID rev2: 1 ide per kartu (Terjemahan / Inpaint / Tentang) + status simpan
// otomatis, agar Settings tidak terasa 1 form panjang yang menakutkan.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Terjemahan", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Terenkripsi di perangkat (EncryptedSharedPreferences). Tidak ikut backup. Tersimpan otomatis tiap ketik.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Field("Agnes AI Key", state.agnesKey, vm::onAgnesChange)
                    Field("Gemini AI Key", state.geminiKey, vm::onGeminiChange)
                    Field("Sumopod Key", state.sumopodKey, vm::onSumopodChange)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Inpaint & Models", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Bubble dibundle di APK bila build menyertakannya. " +
                            "PP-OCR / LaMa / MiGAN TIDAK dibundle — download manual per tombol di layar Models.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onOpenModels, modifier = Modifier.fillMaxWidth()) {
                        Text("Models (Bubble / PP-OCR / LaMa / MiGAN)")
                    }
                    OutlinedButton(onClick = onOpenModels, modifier = Modifier.fillMaxWidth()) {
                        Text("Cek status model")
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tentang", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "VastypR • com.volxsy.vastypr • minSdk 26 • dark-first editor. " +
                            "Alur: Import → Bubble → Clean → Translate → Render → Export.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Sembunyikan key" else "Tampilkan key",
                )
            }
        },
    )
}
