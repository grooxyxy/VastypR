package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.volxsy.vastypr.editor.model.Layer

// Skill: android-mobile-frontend-design — panel layer ala ibisPaint, REDESIGN v2:
// - Header berguna: judul + jumlah layer + tombol aksi berlabel (bukan ikon misterius).
// - Tiap baris: ikon tipe (image/text/folder), nama, badge opacity %, badge CLIP,
//   tombol visibility 48dp, slider opacity + % — semua dalam card terpilih yang jelas.
// - Empty state + hint pilih layer untuk Translate/Edit.
// FLUID: LazyColumn max-height agar tidak measure-loop di BottomSheet.
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Layers",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${layers.size} layer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FilledTonalButton(onClick = onAddText, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Teks", maxLines = 1)
            }
            FilledTonalButton(onClick = onAddImage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Gambar", maxLines = 1)
            }
            FilledTonalButton(onClick = onAddFolder, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Folder", maxLines = 1)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            AssistChip(
                onClick = onDuplicate,
                label = { Text("Duplikat") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onDelete,
                label = { Text("Hapus aktif") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        if (layers.isEmpty()) {
            Text(
                "Belum ada layer. Tambah teks atau gambar dulu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(layers.reversed(), key = { it.id }) { l ->
                    val selected = l.id == activeId
                    val typeIcon = when (l) {
                        is Layer.Text -> Icons.Default.TextFields
                        is Layer.Folder -> Icons.Default.Folder
                        else -> Icons.Default.Image
                    }
                    val typeLabel = when (l) {
                        is Layer.Text -> "Teks"
                        is Layer.Folder -> "Folder"
                        else -> "Gambar"
                    }
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(l.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    typeIcon,
                                    contentDescription = typeLabel,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(
                                    l.name.ifBlank { typeLabel }, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${(l.opacity * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (l.clipToBelow) {
                                        Text(
                                            "CLIP",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (!l.visible) {
                                        Text(
                                            "HIDDEN",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onToggleVisible(l.id) }) {
                                Icon(
                                    if (l.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (l.visible) "Sembunyikan ${l.name}" else "Tampilkan ${l.name}",
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Opasitas", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                            Slider(
                                value = l.opacity,
                                onValueChange = { onOpacity(l.id, it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Text(
                "Tap baris untuk pilih • layer terpilih bisa Edit text / Translate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
