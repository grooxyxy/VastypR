package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.volxsy.vastypr.R
import com.volxsy.vastypr.editor.model.Layer

// Skill: android-mobile-frontend-design — panel layer ala ibisPaint:
// daftar + visibility + opacity + clip + add/delete/duplicate/folder.
// FLUID rev2: LazyColumn dibatasi max-height (sebelumnya unbounded di dalam
// BottomSheet -> measure loop + jank saat drag). Slider opacity tetap ringan
// karena commit ke VM hanya update 1 layer (UndoRedo push memang disengaja).
@Composable
fun LayerSheet(
    layers: List<Layer>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onToggleVisible: (String) -> Unit,
    onOpacity: (String, Float) -> Unit,
    onAddImage: () -> Unit,
    onAddText: () -> Unit,
    onAddFolder: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onAddImage) { Icon(painterResource(R.drawable.ic_tool_layers), contentDescription = "Add layer") }
            IconButton(onClick = onAddText) { Icon(painterResource(R.drawable.ic_tool_text), contentDescription = "Add text") }
            IconButton(onClick = onAddFolder) { Icon(painterResource(R.drawable.ic_tool_folder), contentDescription = "Add folder") }
            IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete layer") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
        ) {
            items(layers.reversed(), key = { it.id }) { l ->
                val selected = l.id == activeId
                Column(
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onSelect(l.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(12.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            l.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (l.clipToBelow) Text("CLIP", style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { onToggleVisible(l.id) }) {
                            Icon(
                                if (l.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    }
                    Slider(
                        value = l.opacity, onValueChange = { onOpacity(l.id, it) },
                        valueRange = 0f..1f,
                    )
                }
            }
        }
    }
}
