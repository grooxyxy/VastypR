package com.volxsy.vastypr.ui.editor.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volxsy.vastypr.data.fonts.FontManager
import com.volxsy.vastypr.editor.model.DefaultPalette
import com.volxsy.vastypr.editor.model.TextEffect
import com.volxsy.vastypr.editor.model.VastAlign
import com.volxsy.vastypr.editor.model.VastTextStyle
import com.volxsy.vastypr.editor.model.composeAlign
import com.volxsy.vastypr.editor.model.composeDecoration
import com.volxsy.vastypr.editor.model.composeLineHeight
import com.volxsy.vastypr.editor.model.composeTracking
import com.volxsy.vastypr.editor.model.wordSpaced
import com.volxsy.vastypr.ui.components.FontPicker

// Skill: android-compose-foundations + android-mobile-frontend-design
// + android-state-management + android-local-persistence-datastore
// REDESIGN v2 — Text editor ala Photoshop tapi rapi:
// - Preview STICKY di atas dengan efek nyata (stroke/glow/shadow/gradient/bg),
//   bukan teks polos — WYSIWYG sama dengan canvas.
// - Seksi kartu: Konten | Font | Gaya & Paragraf | Tipografi | Efek — tiap kartu
//   ElevatedCard + judul + slider berlabel nilai (em/px/sp).
// - ColorRow 40dp + ring tegas + hex desc untuk a11y.
// - Tombol Save/Load style sebagai FilledTonal, Selesai sebagai aksi primer.
@Composable
fun TextEditorDialog(
    initialContent: String,
    initialStyle: VastTextStyle,
    fonts: List<FontManager.FontInfo>,
    getTypeface: (String?) -> android.graphics.Typeface?,
    onImportFont: () -> Unit,
    onDeleteFont: (String) -> Unit,
    onSaveStyle: (VastTextStyle) -> Unit,
    onLoadStyle: () -> VastTextStyle?,
    onDone: (String, VastTextStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf(initialContent) }
    var size by remember { mutableFloatStateOf(initialStyle.fontSizeSp) }
    var color by remember { mutableStateOf(initialStyle.color) }
    var bold by remember { mutableStateOf(initialStyle.bold) }
    var italic by remember { mutableStateOf(initialStyle.italic) }
    var align by remember { mutableStateOf(initialStyle.align) }
    var fontId by remember { mutableStateOf(initialStyle.fontId) }
    var leading by remember { mutableFloatStateOf(initialStyle.lineHeightEm) }
    var tracking by remember { mutableFloatStateOf(initialStyle.letterSpacingEm) }
    var wordGap by remember { mutableFloatStateOf(initialStyle.wordSpacingEm) }
    var paraGap by remember { mutableFloatStateOf(initialStyle.paragraphSpacingEm) }
    var caps by remember { mutableStateOf(initialStyle.allCaps) }
    var ul by remember { mutableStateOf(initialStyle.underline) }
    var strike by remember { mutableStateOf(initialStyle.strike) }

    val initStroke = initialStyle.effects.filterIsInstance<TextEffect.Stroke>().firstOrNull()
    val initShadow = initialStyle.effects.filterIsInstance<TextEffect.DropShadow>().firstOrNull()
    val initGlow = initialStyle.effects.filterIsInstance<TextEffect.OuterGlow>().firstOrNull()
    val initGrad = initialStyle.effects.filterIsInstance<TextEffect.GradientFill>().firstOrNull()
    val initBg = initialStyle.effects.filterIsInstance<TextEffect.Background>().firstOrNull()

    var strokeOn by remember { mutableStateOf(initStroke != null) }
    var strokeColor by remember { mutableStateOf(initStroke?.color ?: Color.Black) }
    var strokeW by remember { mutableFloatStateOf(initStroke?.widthPx ?: 6f) }
    var shadowOn by remember { mutableStateOf(initShadow != null) }
    var shadowColor by remember { mutableStateOf(initShadow?.color ?: Color.Black.copy(alpha = 0.6f)) }
    var shadowBlur by remember { mutableFloatStateOf(initShadow?.blur ?: 8f) }
    var glowOn by remember { mutableStateOf(initGlow != null) }
    var glowColor by remember { mutableStateOf(initGlow?.color ?: Color(0xFF9D7BFF)) }
    var glowR by remember { mutableFloatStateOf(initGlow?.radius ?: 16f) }
    var gradOn by remember { mutableStateOf(initGrad != null) }
    var gradA by remember { mutableStateOf(initGrad?.colors?.getOrNull(0) ?: Color.White) }
    var gradB by remember { mutableStateOf(initGrad?.colors?.getOrNull(1) ?: Color(0xFF9D7BFF)) }
    var bgOn by remember { mutableStateOf(initBg != null) }
    var bgColor by remember { mutableStateOf(initBg?.color ?: Color.Black.copy(alpha = 0.7f)) }

    fun build(): VastTextStyle = VastTextStyle(
        fontSizeSp = size, color = color, bold = bold, italic = italic,
        align = align, fontId = fontId,
        lineHeightEm = leading, letterSpacingEm = tracking,
        wordSpacingEm = wordGap, paragraphSpacingEm = paraGap,
        allCaps = caps, underline = ul, strike = strike,
        effects = buildList {
            if (gradOn) add(TextEffect.GradientFill(listOf(gradA, gradB)))
            if (strokeOn) add(TextEffect.Stroke(strokeColor, strokeW))
            if (glowOn) add(TextEffect.OuterGlow(glowColor, glowR))
            if (shadowOn) add(TextEffect.DropShadow(shadowColor, 0f, 4f, shadowBlur))
            if (bgOn) add(TextEffect.Background(bgColor, 12f, 8f))
        },
    )

    fun applyLoaded(s: VastTextStyle) {
        size = s.fontSizeSp; color = s.color; bold = s.bold
        italic = s.italic; align = s.align; fontId = s.fontId
        leading = s.lineHeightEm; tracking = s.letterSpacingEm
        wordGap = s.wordSpacingEm; paraGap = s.paragraphSpacingEm
        caps = s.allCaps; ul = s.underline; strike = s.strike
        val st = s.effects.filterIsInstance<TextEffect.Stroke>().firstOrNull()
        strokeOn = st != null; st?.let { strokeColor = it.color; strokeW = it.widthPx }
        val sh = s.effects.filterIsInstance<TextEffect.DropShadow>().firstOrNull()
        shadowOn = sh != null; sh?.let { shadowColor = it.color; shadowBlur = it.blur }
        val g = s.effects.filterIsInstance<TextEffect.OuterGlow>().firstOrNull()
        glowOn = g != null; g?.let { glowColor = it.color; glowR = it.radius }
        val gr = s.effects.filterIsInstance<TextEffect.GradientFill>().firstOrNull()
        gradOn = gr != null
        gr?.let { gradA = it.colors.getOrElse(0) { Color.White }; gradB = it.colors.getOrElse(1) { gradA } }
        val b = s.effects.filterIsInstance<TextEffect.Background>().firstOrNull()
        bgOn = b != null; b?.let { bgColor = it.color }
    }

    val previewStyle = build()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Text", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // Live preview WYSIWYG dengan efek nyata
                PreviewCard(content.ifBlank { "Preview" }, previewStyle)
                CardSection("Konten") {
                    TextField(
                        value = content, onValueChange = { content = it },
                        label = { Text("Isi teks (\\n = baris baru, baris kosong = paragraf)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
                CardSection("Font") {
                    FontPicker(
                        fonts = fonts, selectedId = fontId,
                        getTypeface = getTypeface,
                        onSelect = { fontId = it },
                        onImportClick = onImportFont,
                        onDelete = onDeleteFont,
                    )
                    LabeledSlider("Ukuran", "${size.toInt()}sp", size, 12f..120f) { size = it }
                    Text("Warna teks", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    ColorRow(color, onPick = { color = it })
                }
                CardSection("Paragraf & gaya") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VastAlign.entries.forEach { a ->
                            FilterChip(
                                selected = align == a,
                                onClick = { align = a },
                                label = {
                                    Text(
                                        when (a) {
                                            VastAlign.LEFT -> "Kiri"
                                            VastAlign.CENTER -> "Tengah"
                                            VastAlign.RIGHT -> "Kanan"
                                            VastAlign.JUSTIFY -> "Rata"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Bold") })
                        FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("Italic") })
                        FilterChip(selected = caps, onClick = { caps = !caps }, label = { Text("AA") })
                        FilterChip(selected = ul, onClick = { ul = !ul }, label = { Text("U̲") })
                        FilterChip(selected = strike, onClick = { strike = !strike }, label = { Text("S̶") })
                    }
                }
                CardSection("Tipografi") {
                    LabeledSlider("Leading (jarak baris)", "${"%.2f".format(leading)}em", leading, 0.9f..2.5f) { leading = it }
                    LabeledSlider("Tracking (jarak huruf)", "${"%.2f".format(tracking)}em", tracking, -0.1f..0.5f) { tracking = it }
                    LabeledSlider("Word spacing", "${"%.2f".format(wordGap)}em", wordGap, 0f..1f) { wordGap = it }
                    LabeledSlider("Paragraph spacing", "${"%.2f".format(paraGap)}em", paraGap, 0f..1.5f) { paraGap = it }
                    Text(
                        "Paragraph spacing = jeda ekstra tiap batas paragraf (baris kosong). Untuk teks 2 baris biasa cukup Leading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CardSection("Efek (ditumpuk)") {
                    Effect("Stroke", strokeOn, { strokeOn = it }) {
                        ColorRow(strokeColor, onPick = { strokeColor = it })
                        LabeledSlider("Tebal", "${strokeW.toInt()}px", strokeW, 1f..24f) { strokeW = it }
                    }
                    Effect("Shadow", shadowOn, { shadowOn = it }) {
                        ColorRow(shadowColor, onPick = { shadowColor = it })
                        LabeledSlider("Blur", "${shadowBlur.toInt()}", shadowBlur, 0f..32f) { shadowBlur = it }
                    }
                    Effect("Outer Glow", glowOn, { glowOn = it }) {
                        ColorRow(glowColor, onPick = { glowColor = it })
                        LabeledSlider("Radius", "${glowR.toInt()}", glowR, 4f..48f) { glowR = it }
                    }
                    Effect("Gradient Fill", gradOn, { gradOn = it }) {
                        Text("Warna 1"); ColorRow(gradA, onPick = { gradA = it })
                        Text("Warna 2"); ColorRow(gradB, onPick = { gradB = it })
                    }
                    Effect("Background", bgOn, { bgOn = it }) {
                        ColorRow(bgColor, onPick = { bgColor = it })
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onSaveStyle(build()) }) { Text("Save style") }
                    OutlinedButton(onClick = { onLoadStyle()?.let(::applyLoaded) }) { Text("Load style") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(content.ifBlank { "Text" }, build()) }) { Text("Selesai") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun CardSection(title: String, body: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.padding(top = 8.dp)) { body() }
        }
    }
}

@Composable
private fun PreviewCard(text: String, style: VastTextStyle) {
    val bg = style.effects.filterIsInstance<TextEffect.Background>().firstOrNull()
    val shadow = style.effects.filterIsInstance<TextEffect.DropShadow>().firstOrNull()
    val stroke = style.effects.filterIsInstance<TextEffect.Stroke>().firstOrNull()
    val glow = style.effects.filterIsInstance<TextEffect.OuterGlow>().firstOrNull()
    val gradient = style.effects.filterIsInstance<TextEffect.GradientFill>().firstOrNull()
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(bg?.color ?: Color.Transparent)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Tumpuk: glow → stroke (8 arah) → fill/gradient, meniru canvas.
            val ann = style.wordSpaced(text)
            val base = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                fontSize = style.fontSizeSp.coerceAtMost(48f).sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                textAlign = style.composeAlign(),
                lineHeight = style.composeLineHeight(),
                letterSpacing = style.composeTracking(),
                textDecoration = style.composeDecoration(),
            )
            Box(contentAlignment = Alignment.Center) {
                if (glow != null) {
                    Text(
                        ann,
                        style = base.copy(
                            color = glow.color,
                            shadow = Shadow(glow.color, offset = androidx.compose.ui.geometry.Offset.Zero, blurRadius = glow.radius),
                        ),
                    )
                }
                if (stroke != null) {
                    // Outline 8 arah agar terlihat seperti stroke beneran di preview kecil.
                    val r = (stroke.widthPx / 6f).coerceIn(1f, 4f)
                    listOf(
                        -r to 0f, r to 0f, 0f to -r, 0f to r,
                        -r to -r, r to r, -r to r, r to -r,
                    ).forEach { (dx, dy) ->
                        Text(
                            ann,
                            style = base.copy(
                                color = stroke.color,
                                shadow = Shadow(stroke.color, offset = androidx.compose.ui.geometry.Offset(dx, dy), blurRadius = 0.5f),
                            ),
                        )
                    }
                }
                Text(
                    ann,
                    style = if (gradient != null && gradient.colors.size >= 2) {
                        base.copy(
                            brush = Brush.linearGradient(gradient.colors),
                            shadow = shadow?.let { Shadow(it.color, offset = androidx.compose.ui.geometry.Offset(it.dx, it.dy), blurRadius = it.blur) },
                        )
                    } else {
                        base.copy(
                            color = style.color,
                            shadow = shadow?.let { Shadow(it.color, offset = androidx.compose.ui.geometry.Offset(it.dx, it.dy), blurRadius = it.blur) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value = v, onValueChange = onChange, valueRange = range)
}

@Composable
private fun Effect(title: String, on: Boolean, setOn: (Boolean) -> Unit, body: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = on, onCheckedChange = setOn)
    }
    if (on) body()
}

@Composable
private fun ColorRow(current: Color, onPick: (Color) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (listOf(Color.Black, Color.White) + DefaultPalette).distinctBy { it.toArgb() }.forEach { c ->
            val sel = c.toArgb() == current.toArgb()
            Box(
                Modifier.size(40.dp).clip(CircleShape)
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
