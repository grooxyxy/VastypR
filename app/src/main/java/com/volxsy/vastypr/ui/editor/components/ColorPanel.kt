package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Skill: android-mobile-frontend-design — REDESIGN v3:
// - Warna via WheelColorField global (hue ring + SV + alpha + hex + preset),
//   bukan palette-only — sama seperti aplikasi gambar.
// - Preview ukuran brush live + preset S/M/L/XL + slider 2..120px.
@Composable
fun ColorPanel(
    current: Color,
    sizePx: Float,
    onColor: (Color) -> Unit,
    onSize: (Float) -> Unit,
    modifier: Modifier = Modifier,
    eyedrop: EyedropSpec? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Brush",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size((sizePx.coerceIn(2f, 120f) / 120f * 36f + 6.dp.value).dp.coerceIn(8.dp, 42.dp))
                        .clip(CircleShape)
                        .background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
            Text(
                "${sizePx.toInt()}px",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        WheelColorField(current = current, onPick = onColor, label = "Warna brush", eyedrop = eyedrop)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf("S" to 8f, "M" to 24f, "L" to 48f, "XL" to 80f).forEach { (label, v) ->
                FilterChip(
                    selected = sizePx.toInt() == v.toInt(),
                    onClick = { onSize(v) },
                    label = { Text(label) },
                )
            }
        }
        Slider(
            value = sizePx,
            onValueChange = onSize,
            valueRange = 2f..120f,
            modifier = Modifier.semantics { contentDescription = "Ukuran brush ${sizePx.toInt()} pixel" },
        )
        Text(
            "Pilih warna via wheel/hex lalu sapukan di canvas (tool Brush). Penghapus via toggle Brush/Eraser di atas canvas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
