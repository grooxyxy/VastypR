package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Skill: android-compose-foundations + android-networking-retrofit-okhttp
// + android-security-best-practices + android-state-management
// FLUID rev2 + NEXT_STEP P2: tambah "Render ke bubble" — hasil translate langsung
// jadi Layer.Text baru di posisi bubble (auto-fit font), bukan cuma Pakai ke
// layer aktif. Tanpa dep baru.
@Composable
fun TranslateDialog(
    sourceText: String,
    onTranslate: suspend (providerId: String, targetLang: String) -> Result<String>,
    onApply: (translated: String) -> Unit,
    onDismiss: () -> Unit,
    onRenderToBubble: ((translated: String) -> Unit)? = null,
    hasBubbleTarget: Boolean = false,
) {
    var provider by remember { mutableStateOf("gemini") }
    var target by remember { mutableStateOf("id") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun run() {
        scope.launch {
            busy = true
            result = null
            error = null
            onTranslate(provider, target)
                .onSuccess { result = it }
                .onFailure { error = (it.message ?: "Translate gagal").take(300) }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Translate teks") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Asli: ${sourceText.take(200)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Provider", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("gemini", "agnes", "sumopod").forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = { provider = p },
                            label = { Text(p.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Text("Target", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("id", "en", "ja", "ko", "zh").forEach { lang ->
                        FilterChip(
                            selected = target == lang,
                            onClick = { target = lang },
                            label = { Text(lang.uppercase()) },
                        )
                    }
                }
                when {
                    busy -> CircularProgressIndicator()
                    result != null -> Text(
                        "Hasil: $result",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    error != null -> Text(
                        "Gagal: $error — cek API key di Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Butuh internet + API key (Settings). " +
                        "Pakai = timpa layer aktif; Render = buat teks baru di bubble (auto-fit).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = ::run, enabled = !busy) { Text("Translate") }
                    TextButton(
                        onClick = {
                            result?.let {
                                onApply(it)
                                onDismiss()
                            }
                        },
                        enabled = !busy && result != null,
                    ) { Text("Pakai") }
                }
                if (onRenderToBubble != null) {
                    TextButton(
                        onClick = {
                            result?.let { onRenderToBubble(it) }
                        },
                        enabled = !busy && result != null && hasBubbleTarget,
                    ) { Text(if (hasBubbleTarget) "Render ke bubble" else "Render (butuh bubble/seleksi)") }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Tutup") }
        },
        modifier = Modifier.padding(8.dp),
    )
}
