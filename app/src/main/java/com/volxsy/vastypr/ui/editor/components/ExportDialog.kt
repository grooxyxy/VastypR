package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Skill: android-compose-foundations + android-media-files-sharing
// + android-local-persistence-datastore + android-state-management
// Dialog export: format (jpeg/png/webp) + nama file custom + resolusi long-side
// + quality. Tanpa dep baru — hasil diteruskan ke ExportManager via onDone.
@Composable
fun ExportDialog(
    initialName: String,
    initialFormat: String, // "jpeg" | "png" | "webp"
    onDone: (fileName: String, format: String, longSide: Int?, quality: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var format by remember { mutableStateOf(initialFormat.lowercase().takeIf { it in setOf("jpeg", "png", "webp") } ?: "png") }
    var longSideIdx by remember { mutableStateOf(1) } // 0=Asli, 1=2048, 2=1600, 3=1080
    var quality by remember { mutableFloatStateOf(95f) }

    val longSide: Int? = when (longSideIdx) {
        0 -> null
        1 -> 2048
        2 -> 1600
        else -> 1080
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export image") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama file (tanpa ekstensi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("jpeg", "png", "webp").forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = { Text(f.uppercase()) },
                        )
                    }
                }
                Text("Resolusi (sisi panjang)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Asli", "2048", "1600", "1080").forEachIndexed { i, label ->
                        FilterChip(
                            selected = longSideIdx == i,
                            onClick = { longSideIdx = i },
                            label = { Text(label) },
                        )
                    }
                }
                if (format != "png") {
                    Text(
                        "Quality: ${quality.toInt()}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 60f..100f,
                    )
                }
                Text(
                    "Disimpan ke Pictures/VastypR. Render: gambar sumber + image layer + " +
                        "brush + teks, lalu crop bila ada.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(name.trim(), format, longSide, quality.toInt()) },
                enabled = name.trim().isNotEmpty(),
            ) { Text("Export") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Batal") }
        },
        modifier = Modifier.padding(8.dp),
    )
}
