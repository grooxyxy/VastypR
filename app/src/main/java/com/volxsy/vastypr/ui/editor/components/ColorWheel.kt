package com.volxsy.vastypr.ui.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.volxsy.vastypr.editor.model.DefaultPalette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Skill: android-compose-foundations + android-mobile-frontend-design
// Color wheel ala aplikasi gambar (ibisPaint/MediBang): lingkaran Hue +
// kotak Saturation-Value di tengah + slider Alpha + kolom Hex — TANPA dep baru
// (HSV via android.graphics.Color bawaan, minSdk 26).
// Dipakai GLOBAL menggantikan ColorRow palette-only di: ColorPanel (brush),
// TextEditorDialog (warna teks + semua efek). API:
// - VastColorWheel: wheel penuh (untuk dialog).
// - WheelColorField: baris kompak (swatch + hex + tombol Wheel) + preset cepat.

private val HueStops = List(7) { i ->
    hsv(360f * i / 6f, 1f, 1f, 1f)
}

private fun hsv(h: Float, s: Float, v: Float, a: Float): Color {
    val argb = android.graphics.Color.HSVToColor(
        (a.coerceIn(0f, 1f) * 255).toInt(),
        floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)),
    )
    return Color(argb)
}

private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return out
}

private fun Color.alphaF(): Float = (toArgb() ushr 24) / 255f

private fun toHex(c: Color): String {
    val a = (c.alphaF() * 255).toInt()
    val r = (c.red * 255).toInt()
    val g = (c.green * 255).toInt()
    val b = (c.blue * 255).toInt()
    return if (a >= 255) "#%02X%02X%02X".format(r, g, b)
    else "#%02X%02X%02X%02X".format(a, r, g, b)
}

private fun parseHex(raw: String): Color? = runCatching {
    var s = raw.trim().removePrefix("#")
    if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
    when (s.length) {
        6 -> {
            val v = s.toLong(16)
            Color(0xFF000000L or v)
        }
        8 -> {
            val v = s.toULong(16)
            Color(v.toLong())
        }
        else -> null
    }
}.getOrNull()

@Composable
fun VastColorWheel(
    current: Color,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hue by remember(current) { mutableFloatStateOf(current.toHsv()[0]) }
    var sat by remember(current) { mutableFloatStateOf(current.toHsv()[1]) }
    var value by remember(current) { mutableFloatStateOf(current.toHsv()[2]) }
    var alpha by remember(current) { mutableFloatStateOf(current.alphaF()) }
    var hex by remember(current) { mutableStateOf(toHex(current)) }

    fun emit(h: Float = hue, s: Float = sat, v: Float = value, a: Float = alpha) {
        hue = h; sat = s; value = v; alpha = a
        val c = hsv(h, s, v, a)
        hex = toHex(c)
        onPick(c)
    }

    // Hex diketik manual -> update wheel juga.
    fun emitHex(raw: String) {
        hex = raw
        val c = parseHex(raw) ?: return
        val h = c.toHsv()
        hue = h[0]; sat = h[1]; value = h[2]; alpha = c.alphaF()
        onPick(c)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(216.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val down = ev.type ==
                                    androidx.compose.ui.input.pointer.PointerEventType.Press ||
                                    ev.type ==
                                    androidx.compose.ui.input.pointer.PointerEventType.Move
                                if (!down) continue
                                ev.changes.forEach { it.consume() }
                                val pos = ev.changes.firstOrNull()?.position ?: continue
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val cx = w / 2f
                                val cy = h / 2f
                                val dx = pos.x - cx
                                val dy = pos.y - cy
                                val dist = kotlin.math.hypot(dx, dy)
                                val rOut = w / 2f
                                val rRing = rOut * 0.20f // tebal ring hue
                                val halfSv = rOut - rRing - 10f // setengah sisi kotak SV
                                if (dist > halfSv * 1.42f) {
                                    // Zona hue ring.
                                    var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                                    deg = (deg + 360f) % 360f
                                    emit(h = deg)
                                } else {
                                    // Zona kotak SV.
                                    val s = ((dx + halfSv) / (2f * halfSv)).coerceIn(0f, 1f)
                                    val v = (1f - (dy + halfSv) / (2f * halfSv)).coerceIn(0f, 1f)
                                    emit(s = s, v = v)
                                }
                            }
                        }
                    },
            ) {
                val w = size.width
                val cx = w / 2f
                val ringW = w * 0.20f
                // 1. Ring hue.
                drawArc(
                    brush = Brush.sweepGradient(HueStops),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(ringW / 2f, ringW / 2f),
                    size = Size(w - ringW, w - ringW),
                    style = Stroke(width = ringW),
                )
                // 2. Kotak SV di tengah.
                val half = w / 2f - ringW - 6f
                val tl = Offset(cx - half, cx - half)
                val sz = Size(half * 2f, half * 2f)
                val hueBase = hsv(hue, 1f, 1f, 1f)
                drawRect(hueBase, tl, sz)
                drawRect(
                    Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
                    tl, sz,
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                    tl, sz,
                )
                // 3. Penanda hue (titik di ring).
                val rad = Math.toRadians(hue.toDouble())
                val rr = w / 2f - ringW / 2f
                val hp = Offset(
                    cx + (cos(rad) * rr).toFloat(),
                    cx + (sin(rad) * rr).toFloat(),
                )
                drawCircle(Color.White, radius = 13f, center = hp)
                drawCircle(hsv(hue, 1f, 1f, 1f), radius = 9f, center = hp)
                // 4. Penanda SV.
                val sp = Offset(cx - half + sat * half * 2f, cx - half + (1f - value) * half * 2f)
                drawCircle(Color.White, radius = 11f, center = sp)
                drawCircle(hsv(hue, sat, value, 1f), radius = 7f, center = sp)
            }
        }
        // Alpha.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alpha", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = alpha, onValueChange = { emit(a = it) },
                valueRange = 0f..1f, modifier = Modifier.weight(1f),
            )
            Text("${(alpha * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        // Hex + preview.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(hsv(hue, sat, value, alpha))
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
            OutlinedTextField(
                value = hex, onValueChange = ::emitHex,
                label = { Text("Hex (#RRGGBB / #AARRGGBB)") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Baris kompak pengganti ColorRow lama: preset cepat + tombol wheel penuh. */
@Composable
fun WheelColorField(
    current: Color,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Warna",
) {
    var showWheel by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(current)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { showWheel = true },
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    toHex(current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showWheel = true }) { Text("Wheel…") }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(
                (listOf(Color.Black, Color.White) + DefaultPalette).distinctBy { it.toArgb() },
                key = { it.toArgb() },
            ) { c ->
                val sel = c.toArgb() == current.toArgb()
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (sel) 3.dp else 1.dp,
                            color = if (sel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onPick(c) },
                )
            }
        }
    }
    if (showWheel) {
        var draft by remember(current) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { showWheel = false },
            title = { Text("Pilih warna — $label") },
            text = {
                VastColorWheel(current = draft, onPick = { draft = it })
            },
            confirmButton = {
                TextButton(onClick = { onPick(draft); showWheel = false }) { Text("Pakai") }
            },
            dismissButton = {
                TextButton(onClick = { showWheel = false }) { Text("Batal") }
            },
        )
    }
}
