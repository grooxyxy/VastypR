package com.volxsy.vastypr.ui.components

import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.volxsy.vastypr.data.fonts.FontManager

// Skill: android-compose-foundations + android-compose-performance
// Preview font REAL (TextView + cached Typeface) — bukan sekadar nama file.
// Fast load: Typeface di-LruCache (8), preload saat refresh.
// Import: tombol → caller membuka SAF picker (font/*), uri dikirim ke FontManager.import.
@Composable
fun FontPicker(
    fonts: List<FontManager.FontInfo>,
    selectedId: String?,
    getTypeface: (String?) -> android.graphics.Typeface?,
    onSelect: (String?) -> Unit,
    onImportClick: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(fonts, query) {
        if (query.isBlank()) fonts
        else fonts.filter { it.displayName.contains(query, true) }
    }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Cari font") },
                modifier = Modifier.weight(1f), singleLine = true,
            )
            OutlinedButton(onClick = onImportClick, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Import")
            }
        }
        LazyColumn(Modifier.heightIn(max = 260.dp)) {
            items(shown, key = { it.id }) { f ->
                val tf = remember(f.id) { getTypeface(f.id.takeIf { it != FontManager.SYSTEM_DEFAULT }) }
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onSelect(f.id.takeIf { it != FontManager.SYSTEM_DEFAULT }) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (selectedId == f.id || (selectedId == null && f.id == FontManager.SYSTEM_DEFAULT)) "● " else "○ ") + f.displayName +
                                (if (f.isImported) " • ${f.sizeKb}KB" else ""),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        AndroidView(
                            factory = { c ->
                                TextView(c).apply {
                                    text = FontManager.PREVIEW_TEXT
                                    textSize = 18f
                                    typeface = tf
                                }
                            },
                            update = { it.typeface = tf },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (f.isImported) {
                        IconButton(onClick = { onDelete(f.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus font")
                        }
                    }
                }
            }
        }
    }
}
