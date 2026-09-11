package com.volxsy.vastypr.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volxsy.vastypr.data.fonts.FontManager
import com.volxsy.vastypr.data.local.ProjectDao
import com.volxsy.vastypr.data.prefs.UserPrefs
import com.volxsy.vastypr.editor.engine.LayerManager
import com.volxsy.vastypr.editor.engine.TallImageManager
import com.volxsy.vastypr.editor.engine.UndoRedoManager
import com.volxsy.vastypr.editor.io.ExportManager
import com.volxsy.vastypr.editor.model.BrushMode
import com.volxsy.vastypr.editor.model.EditorEvent
import com.volxsy.vastypr.editor.model.EditorTool
import com.volxsy.vastypr.editor.model.EditorUiState
import com.volxsy.vastypr.editor.model.Layer
import com.volxsy.vastypr.editor.model.TextStyleJson
import com.volxsy.vastypr.editor.model.VastTextStyle
import com.volxsy.vastypr.editor.tools.BrushStroke
import com.volxsy.vastypr.ml.detection.Bubble
import com.volxsy.vastypr.ml.detection.BubbleDetector
import com.volxsy.vastypr.ml.detection.MaskBuilder
import com.volxsy.vastypr.ml.detection.OcrEngine
import com.volxsy.vastypr.ml.detection.TextMaskProvider
import com.volxsy.vastypr.ml.inpaint.Inpainter
import com.volxsy.vastypr.ml.models.InpaintBackend
import com.volxsy.vastypr.translation.TranslationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named

// Skill: android-state-management + android-architecture-clean + android-kotlin-core
// + android-coroutines-flow + android-compose-state-effects
// + android-media-files-sharing + android-networking-retrofit-okhttp
// + android-compose-performance
// Model ML TIDAK di-download di sini — user memasukkan file .onnx manual ke
// filesDir/models (atau via Settings → Models). Detektor/inpainter melempar
// MissingModelException dengan instruksi bila file belum ada.
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val fonts: FontManager,
    private val prefs: UserPrefs,
    private val exporter: ExportManager,
    private val dao: ProjectDao,
    private val tall: TallImageManager,
    @ApplicationContext private val appCtx: Context,
    private val detector: BubbleDetector,
    private val ocr: OcrEngine,
    private val textMask: TextMaskProvider,
    @Named("telea") private val telea: Inpainter,
    @Named("lama") private val lama: Inpainter,
    @Named("migan") private val migan: Inpainter,
    @Named("agnes") private val agnes: TranslationProvider,
    @Named("gemini") private val gemini: TranslationProvider,
    @Named("sumopod") private val sumopod: TranslationProvider,
) : ViewModel() {

    // Snapshot dokumen untuk undo/redo: layers + brush strokes (immutable, aman
    // dipegang referensinya karena semua update copy-on-write).
    private data class DocSnapshot(val layers: List<Layer>, val strokes: List<BrushStroke>)

    private val history = UndoRedoManager<DocSnapshot>()

    private val _uiState = MutableStateFlow(
        EditorUiState(
            layers = listOf(
                Layer.Image(id = "bg", name = "Background"),
                Layer.Text(id = "t1", name = "Title", content = "VastypR"),
            ),
            activeLayerId = "t1",
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(replay = 0)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    val fontList = fonts.fonts
    private var savedStyle: VastTextStyle? = null

    // Brush strokes sesi aktif (dirender di Canvas, di-merge jadi layer saat MVP-2).
    private val _strokes = MutableStateFlow<List<BrushStroke>>(emptyList())
    val strokes: StateFlow<List<BrushStroke>> = _strokes.asStateFlow()
    private var currentStroke: BrushStroke? = null
    private var strokeCountAtStart = 0

    private fun snap() = DocSnapshot(_uiState.value.layers, _strokes.value)

    init {
        history.reset(snap())
        viewModelScope.launch {
            fonts.refresh()
            prefs.textStyleJson().first()?.let { savedStyle = TextStyleJson.decode(it) }
        }
    }

    private fun commitLayers(next: List<Layer>) {
        _uiState.update { it.copy(layers = next) }
        history.push(snap())
        _uiState.update {
            it.copy(canUndo = history.canUndo(), canRedo = history.canRedo())
        }
    }

    // ---- Tool ----
    fun selectTool(t: EditorTool) = _uiState.update { it.copy(tool = t) }
    fun setBrush(color: Color, sizePx: Float, mode: BrushMode) =
        _uiState.update { it.copy(brushColor = color, brushSizePx = sizePx, brushMode = mode) }

    fun pickColor(c: Color) = _uiState.update { it.copy(brushColor = c) } // eyedrop result

    // ---- Layer ops (syarat: add/delete/duplicate/copy/clip/folder) ----
    fun addImageLayer() = commitLayers(_uiState.value.layers + LayerManager.addImage("Image ${_uiState.value.layers.size}"))
    fun addTextLayer() = commitLayers(_uiState.value.layers + LayerManager.addText("Text ${_uiState.value.layers.size}"))

    /** TEXT tool tap: tambah teks tepat di posisi tap (normalisasi 0..1 → offset -0.45..0.45). */
    fun addTextAt(nx: Float, ny: Float, content: String = "New text") {
        val ox = (nx - 0.5f).coerceIn(-0.45f, 0.45f)
        val oy = (ny - 0.5f).coerceIn(-0.45f, 0.45f)
        val base = LayerManager.addText(content)
        val placed = base.copy(offsetX = ox, offsetY = oy, name = content.take(16).ifBlank { "Text" })
        commitLayers(_uiState.value.layers + placed)
        setActive(placed.id)
        toast("Teks ditambah — tap teks untuk pilih, seret dengan Move")
    }
    fun addFolder() = commitLayers(_uiState.value.layers + LayerManager.addFolder())
    fun deleteActive() {
        val id = _uiState.value.activeLayerId ?: return
        commitLayers(LayerManager.delete(_uiState.value.layers, id))
    }
    fun duplicateActive() {
        val id = _uiState.value.activeLayerId ?: return
        commitLayers(LayerManager.duplicate(_uiState.value.layers, id))
    }
    fun setActive(id: String) = _uiState.update { it.copy(activeLayerId = id) }
    fun toggleVisible(id: String) = commitLayers(LayerManager.toggleVisible(_uiState.value.layers, id))
    fun setOpacity(id: String, o: Float) = commitLayers(LayerManager.setOpacity(_uiState.value.layers, id, o))
    fun setClip(id: String, clip: Boolean) = commitLayers(LayerManager.setClip(_uiState.value.layers, id, clip))
    fun moveLayer(from: Int, to: Int) = commitLayers(LayerManager.move(_uiState.value.layers, from, to))

    /** MOVE tool: geser layer aktif. Dipanggil dari drag (delta fraksi) — tanpa history spam:
     *  commit tiap gesture-end via nudgeCommit. */
    fun nudgeActiveLive(dx: Float, dy: Float) {
        val id = _uiState.value.activeLayerId ?: return
        _uiState.update {
            it.copy(layers = LayerManager.nudgeOffset(it.layers, id, dx, dy))
        }
    }

    fun nudgeActiveCommit() {
        history.push(snap())
        _uiState.update { it.copy(canUndo = history.canUndo(), canRedo = history.canRedo()) }
    }

    // ---- Text edit (TextEditorDialog) ----
    fun activeTextLayer(): Layer.Text? =
        _uiState.value.layers.filterIsInstance<Layer.Text>()
            .firstOrNull { it.id == _uiState.value.activeLayerId }

    fun updateActiveText(content: String, style: VastTextStyle) {
        val id = _uiState.value.activeLayerId ?: return
        commitLayers(LayerManager.updateText(_uiState.value.layers, id, content, style))
    }

    fun saveTextStyle(style: VastTextStyle) {
        viewModelScope.launch {
            prefs.saveTextStyleJson(TextStyleJson.encode(style))
            savedStyle = style
            _events.emit(EditorEvent.Message("Text style tersimpan"))
        }
    }

    fun getSavedStyle(): VastTextStyle? = savedStyle

    // ---- Font (FontManager: preview + fast load + import) ----
    fun fontTypeface(fontId: String?): Typeface? = fonts.getTypeface(fontId)

    fun importFont(uri: Uri) {
        viewModelScope.launch { _events.emit(EditorEvent.Message(fonts.import(uri))) }
    }

    fun deleteFont(id: String) {
        viewModelScope.launch { _events.emit(EditorEvent.Message(fonts.delete(id))) }
    }

    // ---- Undo/Redo (layers + brush strokes) ----
    fun undo() {
        val prev = history.undo(snap()) ?: return
        _strokes.value = prev.strokes
        _uiState.update { it.copy(layers = prev.layers, canUndo = history.canUndo(), canRedo = history.canRedo()) }
    }
    fun redo() {
        val next = history.redo(snap()) ?: return
        _strokes.value = next.strokes
        _uiState.update { it.copy(layers = next.layers, canUndo = history.canUndo(), canRedo = history.canRedo()) }
    }

    // ---- Brush (1 sapuan = 1 langkah undo) ----
    fun brushStart(x: Float, y: Float) {
        strokeCountAtStart = _strokes.value.size
        val s = _uiState.value
        currentStroke = BrushStroke(
            points = listOf(Offset(x, y)),
            color = s.brushColor, sizePx = s.brushSizePx,
            erase = s.brushMode == BrushMode.ERASE,
        )
    }
    fun brushMove(x: Float, y: Float) {
        val cur = currentStroke ?: return
        currentStroke = cur.copy(points = cur.points + Offset(x, y))
        val running = currentStroke ?: return
        _strokes.update { list ->
            if (list.isEmpty()) listOf(running) else list.dropLast(1) + running
        }
    }
    fun brushEnd() {
        currentStroke = null
        // Hanya dorong history bila sapuan ini menambah/memanjang stroke.
        if (_strokes.value.size != strokeCountAtStart) {
            history.push(snap())
            _uiState.update { it.copy(canUndo = history.canUndo(), canRedo = history.canRedo()) }
        }
    }

    fun clearStrokes() {
        if (_strokes.value.isEmpty()) return
        _strokes.update { emptyList() }
        history.push(snap())
        _uiState.update { it.copy(canUndo = history.canUndo(), canRedo = history.canRedo()) }
    }

    // ---- Canvas mapping (ContentScale.Fit, single source of truth) ----
    // Screen menggambar gambar sumber dengan Fit; semua gesture di-normalisasi
    // 0..1 di sini agar konsisten dengan overlay, export, dan mask ML.
    fun setCanvasSize(w: Int, h: Int) {
        val s = _uiState.value
        if (s.canvasWidth != w || s.canvasHeight != h) {
            _uiState.update { it.copy(canvasWidth = w, canvasHeight = h) }
        }
    }

    /** [ox, oy, dw, dh] rect gambar di dalam canvas (px). Full-box bila ukuran tak diketahui. */
    fun fitRectFor(cw: Float, ch: Float): FloatArray {
        val s = _uiState.value
        val iw = s.imageWidth.toFloat()
        val ih = s.imageHeight.toFloat()
        if (cw <= 0 || ch <= 0 || iw <= 0 || ih <= 0) return floatArrayOf(0f, 0f, cw, ch)
        val scale = minOf(cw / iw, ch / ih)
        val dw = iw * scale
        val dh = ih * scale
        return floatArrayOf((cw - dw) / 2f, (ch - dh) / 2f, dw, dh)
    }

    /** Canvas px -> normalisasi 0..1, null bila di luar gambar. */
    fun canvasToNormalized(px: Float, py: Float): Offset? {
        val s = _uiState.value
        val f = fitRectFor(s.canvasWidth.toFloat(), s.canvasHeight.toFloat())
        if (f[2] <= 0 || f[3] <= 0) return null
        val nx = (px - f[0]) / f[2]
        val ny = (py - f[1]) / f[3]
        if (nx !in 0f..1f || ny !in 0f..1f) return null
        return Offset(nx, ny)
    }

    // ---- Crop (non-destruktif, diterapkan saat export) ----
    private var cropStart: Offset? = null

    fun startCrop(px: Float, py: Float) {
        val n = canvasToNormalized(px, py) ?: return
        cropStart = n
        _uiState.update { it.copy(cropRect = RectF(n.x, n.y, n.x, n.y)) }
    }

    fun updateCrop(px: Float, py: Float) {
        val a = cropStart ?: return
        val b = canvasToNormalized(px, py) ?: return
        _uiState.update {
            it.copy(
                cropRect = RectF(
                    minOf(a.x, b.x), minOf(a.y, b.y),
                    maxOf(a.x, b.x), maxOf(a.y, b.y),
                )
            )
        }
    }

    fun clearCrop() {
        cropStart = null
        _uiState.update { it.copy(cropRect = null) }
    }

    // ---- Selection rect ----
    private var selStart: Offset? = null

    fun startSelection(px: Float, py: Float) {
        val n = canvasToNormalized(px, py) ?: return
        selStart = n
        _uiState.update { it.copy(selectionRect = RectF(n.x, n.y, n.x, n.y)) }
    }

    fun updateSelection(px: Float, py: Float) {
        val a = selStart ?: return
        val b = canvasToNormalized(px, py) ?: return
        _uiState.update {
            it.copy(
                selectionRect = RectF(
                    minOf(a.x, b.x), minOf(a.y, b.y),
                    maxOf(a.x, b.x), maxOf(a.y, b.y),
                )
            )
        }
    }

    fun clearSelection() {
        selStart = null
        _uiState.update { it.copy(selectionRect = null) }
    }

    // ---- Lasso (bebas, bisa jadi mask inpaint) ----
    fun startLasso(px: Float, py: Float) {
        val n = canvasToNormalized(px, py) ?: return
        _uiState.update { it.copy(lassoPoints = listOf(n)) }
    }

    fun appendLasso(px: Float, py: Float) {
        val n = canvasToNormalized(px, py) ?: return
        _uiState.update { s ->
            val last = s.lassoPoints.lastOrNull()
            // Batasi kepadatan titik agar state tetap ringan (jarak min 0.002, maks 2000).
            if (last != null && (n - last).getDistance() < 0.002f) return@update s
            if (s.lassoPoints.size >= 2000) return@update s
            s.copy(lassoPoints = s.lassoPoints + n)
        }
    }

    fun finishLasso() {
        if (_uiState.value.lassoPoints.size < 3) {
            _uiState.update { it.copy(lassoPoints = emptyList()) }
        }
    }

    fun clearLasso() = _uiState.update { it.copy(lassoPoints = emptyList()) }

    // ---- Detection overlays ----
    fun clearDetections() = _uiState.update { it.copy(bubbles = emptyList(), ocrLines = emptyList()) }

    // ---- Export (ExportManager, tanpa dep baru) ----
    suspend fun lastExportFormat(): String = prefs.exportFormat.first()

    fun exportCurrent(fileName: String, formatStr: String, longSide: Int?, quality: Int) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            setBusy("Export…")
            try {
                val format = when (formatStr.lowercase()) {
                    "jpeg", "jpg" -> ExportManager.Format.JPEG
                    "webp" -> ExportManager.Format.WEBP
                    else -> ExportManager.Format.PNG
                }
                prefs.setExportFormat(formatStr.lowercase())
                val bmp = withContext(Dispatchers.Default) { renderLayersToBitmap() }
                val uri = exporter.export(
                    ExportManager.Request(
                        bitmap = bmp,
                        fileName = fileName.ifBlank { "VastypR_${System.currentTimeMillis()}" },
                        format = format,
                        targetLongSide = longSide,
                        quality = quality.coerceIn(60, 100),
                    )
                )
                bmp.recycle()
                _events.emit(EditorEvent.ExportDone(uri.toString()))
            } catch (e: Exception) {
                _events.emit(EditorEvent.Message("Export gagal: ${(e.message ?: "unknown").take(200)}"))
            } finally {
                clearBusy()
            }
        }
    }

    // Render Step 5 full-fidelity via android.graphics murni (tanpa dep baru):
    // gambar sumber (sampled aman OOM) + image layers + brush strokes + text.
    // Terakhir: cropRect non-destruktif bila ada.
    private suspend fun renderLayersToBitmap(): Bitmap {
        val s = _uiState.value
        val w = s.imageWidth.takeIf { it > 0 } ?: 1080
        val h = s.imageHeight.takeIf { it > 0 } ?: 1920
        var bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF23242F.toInt())

        // 0. Gambar sumber sebagai base (decode sampled sesuai ukuran export).
        s.sourceUri?.let { uriStr ->
            runCatching {
                val sampled = tall.decodeSampled(Uri.parse(uriStr), maxOf(w, h))
                canvas.drawBitmap(sampled, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                if (!sampled.isRecycled) sampled.recycle()
            }
        }

        // 1. Image layers (bitmapPath file; placeholder bila kosong).
        s.layers.filterIsInstance<Layer.Image>().filter { it.visible }.forEach { img ->
            val alpha = (img.opacity.coerceIn(0f, 1f) * 255).toInt()
            val loaded = img.bitmapPath?.let { decodeFileFit(it, w, h) }
            if (loaded != null) {
                val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
                canvas.drawBitmap(loaded, null, android.graphics.Rect(0, 0, w, h), p)
                if (!loaded.isRecycled) loaded.recycle()
            } else {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x553ECF8E
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(2f, w / 300f)
                    this.alpha = alpha
                }
                val m = w * 0.05f
                canvas.drawRect(m, h * 0.3f, w - m, h * 0.7f, p)
            }
        }

        // 2. Brush strokes (canvas px -> normalisasi -> export px).
        drawStrokes(canvas, s, w, h)

        // 3. Text layers (tipografi Photoshop + efek: stroke/gradient/shadow/blur/bg).
        var y = h * 0.12f
        s.layers.filterIsInstance<Layer.Text>().filter { it.visible }.forEach { t ->
            val st = t.style
            val shown = if (st.allCaps) t.content.uppercase() else t.content
            val stroke = st.effects.filterIsInstance<com.volxsy.vastypr.editor.model.TextEffect.Stroke>().firstOrNull()
            val shadowFx = st.effects.filterIsInstance<com.volxsy.vastypr.editor.model.TextEffect.DropShadow>().firstOrNull()
            val gradFill = st.effects.filterIsInstance<com.volxsy.vastypr.editor.model.TextEffect.GradientFill>()
                .firstOrNull()?.takeIf { it.colors.size >= 2 }
            val blurFx = st.effects.filterIsInstance<com.volxsy.vastypr.editor.model.TextEffect.Blur>().firstOrNull()
            val bgFx = st.effects.filterIsInstance<com.volxsy.vastypr.editor.model.TextEffect.Background>().firstOrNull()
            val layerAlpha = (t.opacity.coerceIn(0f, 1f) * 255).toInt()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = st.color.toArgb()
                alpha = layerAlpha
                textSize = st.fontSizeSp * 3f
                textAlign = Paint.Align.LEFT // x dihitung manual per align
                letterSpacing = st.letterSpacingEm
                isUnderlineText = st.underline // getter Paint: isUnderlineText()
                isStrikeThruText = st.strike
                fonts.getTypeface(st.fontId)?.let { tf ->
                    val tfStyle = when {
                        st.bold && st.italic -> Typeface.BOLD_ITALIC
                        st.bold -> Typeface.BOLD
                        st.italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    typeface = Typeface.create(tf, tfStyle)
                }
                // Shadow solid / gradasi (gradasi diwakili warna pertama).
                val shCol = shadowFx?.gradient?.firstOrNull() ?: shadowFx?.color
                if (shadowFx != null && shCol != null) {
                    val k = textSize / 28f
                    setShadowLayer(shadowFx.blur * k, shadowFx.dx * k, shadowFx.dy * k, shCol.toArgb())
                }
                // Blur lembut ala Photoshop.
                if (blurFx != null) {
                    maskFilter = android.graphics.BlurMaskFilter(
                        blurFx.radius * (textSize / 28f),
                        android.graphics.BlurMaskFilter.Blur.NORMAL,
                    )
                }
            }
            val anchorX = w * (0.5f + t.offsetX)
            var baseline = y + t.offsetY * h
            val lineH = paint.textSize * st.lineHeightEm
            val paraExtra = paint.textSize * st.paragraphSpacingEm
            val wordGap = paint.textSize * st.wordSpacingEm
            // Ukur dulu semua baris agar LEFT/RIGHT/JUSTIFY punya acuan maxLineW.
            data class Line(val words: List<String>, val totalW: Float)
            // total = Σ kata (measureText sudah termasuk letterSpacing) + wordGap × spasi.
            fun measureLine(line: String): Line {
                val words = line.split(" ").filter { it.isNotEmpty() }
                if (words.isEmpty()) return Line(emptyList(), 0f)
                val total = words.sumOf { paint.measureText(it).toDouble() }.toFloat() +
                    wordGap * (words.size - 1)
                return Line(words, total)
            }
            val paras = shown.split("\n\n")
            val maxLineW = paras.flatMap { it.split("\n") }
                .maxOfOrNull { measureLine(it).totalW } ?: 0f
            // Kumpulkan baris dulu agar background bisa digambar di belakang teks.
            data class Placed(val words: List<String>, val xs: List<Float>, val baseline: Float)
            val placed = mutableListOf<Placed>()
            var bl = baseline
            paras.forEachIndexed { pi, para ->
                if (pi > 0) bl += paraExtra
                para.split("\n").forEachIndexed { li, rawLine ->
                    bl += lineH
                    if (bl >= h * 0.99f) return@forEachIndexed
                    val ln = measureLine(rawLine)
                    if (ln.words.isEmpty()) return@forEachIndexed
                    val isLast = li == para.split("\n").lastIndex
                    val gaps = ln.words.size - 1
                    val gap = when {
                        st.align == com.volxsy.vastypr.editor.model.VastAlign.JUSTIFY &&
                            !isLast && gaps > 0 && maxLineW > ln.totalW ->
                            wordGap + (maxLineW - ln.totalW) / gaps
                        else -> wordGap
                    }
                    val drawW = ln.totalW + if (
                        st.align == com.volxsy.vastypr.editor.model.VastAlign.JUSTIFY &&
                        !isLast && gaps > 0 && maxLineW > ln.totalW
                    ) (maxLineW - ln.totalW) else 0f
                    var x = when (st.align) {
                        com.volxsy.vastypr.editor.model.VastAlign.LEFT -> anchorX - maxLineW / 2f
                        com.volxsy.vastypr.editor.model.VastAlign.RIGHT -> anchorX + maxLineW / 2f - ln.totalW
                        com.volxsy.vastypr.editor.model.VastAlign.JUSTIFY -> anchorX - maxLineW / 2f
                        else -> anchorX - drawW / 2f // CENTER: pusatkan tiap baris
                    }
                    val xs = mutableListOf<Float>()
                    ln.words.forEachIndexed { wi, word ->
                        xs += x
                        x += paint.measureText(word) + if (wi < gaps) gap else 0f
                    }
                    placed += Placed(ln.words, xs, bl)
                }
            }
            // Background di belakang seluruh blok teks.
            if (bgFx != null && placed.isNotEmpty()) {
                val pad = paint.textSize * 0.35f
                val l = (anchorX - maxLineW / 2f - pad).coerceAtLeast(0f)
                val r = (anchorX + maxLineW / 2f + pad).coerceAtMost(w.toFloat())
                val top = (placed.first().baseline + paint.ascent() - pad * 0.4f).coerceAtLeast(0f)
                val bottom = (placed.last().baseline + paint.descent() + pad * 0.4f)
                    .coerceAtMost(h.toFloat())
                val bgp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = bgFx.color.toArgb()
                    alpha = layerAlpha
                }
                canvas.drawRoundRect(
                    android.graphics.RectF(l, top, r, bottom),
                    bgFx.cornerPx * (paint.textSize / 28f),
                    bgFx.cornerPx * (paint.textSize / 28f),
                    bgp,
                )
            }
            // Paint outline (solid / gradasi horizontal selebar blok).
            val strokePaint = stroke?.let { se ->
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    alpha = layerAlpha
                    textSize = paint.textSize
                    textAlign = Paint.Align.LEFT
                    letterSpacing = st.letterSpacingEm
                    typeface = paint.typeface
                    strokeWidth = (se.widthPx * (paint.textSize / 28f)).coerceAtLeast(1f)
                    val g = se.gradient?.takeIf { it.size >= 2 }
                    if (g != null) {
                        shader = android.graphics.LinearGradient(
                            anchorX - maxLineW / 2f, 0f, anchorX + maxLineW / 2f, 0f,
                            g[0].toArgb(), g[1].toArgb(),
                            android.graphics.Shader.TileMode.CLAMP,
                        )
                    } else {
                        color = se.color.toArgb()
                    }
                }
            }
            // Gradient fill untuk teks utama.
            if (gradFill != null) {
                paint.shader = android.graphics.LinearGradient(
                    anchorX - maxLineW / 2f, 0f, anchorX + maxLineW / 2f, 0f,
                    gradFill.colors[0].toArgb(), gradFill.colors[1].toArgb(),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
            placed.forEach { p ->
                p.words.forEachIndexed { wi, word ->
                    if (strokePaint != null) canvas.drawText(word, p.xs[wi], p.baseline, strokePaint)
                    canvas.drawText(word, p.xs[wi], p.baseline, paint)
                }
                baseline = p.baseline
            }
            y = baseline + paint.textSize * 0.6f
            if (y > h * 0.99f) return@forEach
        }

        // 4. Crop non-destruktif.
        s.cropRect?.let { r ->
            val l = (r.left * w).toInt().coerceIn(0, w - 1)
            val t = (r.top * h).toInt().coerceIn(0, h - 1)
            val rr = (r.right * w).toInt().coerceIn(l + 1, w)
            val b = (r.bottom * h).toInt().coerceIn(t + 1, h)
            if (rr - l >= 8 && b - t >= 8) {
                val cropped = Bitmap.createBitmap(bmp, l, t, rr - l, b - t)
                if (cropped !== bmp) bmp.recycle()
                bmp = cropped
            }
        }
        return bmp
    }

    private fun drawStrokes(canvas: Canvas, s: EditorUiState, w: Int, h: Int) {
        val cw = s.canvasWidth.toFloat()
        val ch = s.canvasHeight.toFloat()
        _strokes.value.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            // Petakan tiap titik via normalisasi (akurat Fit); fallback skala proporsional.
            val pts = if (cw > 0 && ch > 0) {
                val f = fitRectFor(cw, ch)
                stroke.points.map { p ->
                    val nx = ((p.x - f[0]) / f[2]).coerceIn(0f, 1f)
                    val ny = ((p.y - f[1]) / f[3]).coerceIn(0f, 1f)
                    Offset(nx * w, ny * h)
                }
            } else {
                val sx = w / 1080f
                stroke.points.map { Offset(it.x * sx, it.y * sx) }
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = stroke.color.toArgb()
                strokeWidth = (stroke.sizePx * if (cw > 0) w / cw else w / 1080f).coerceAtLeast(1f)
                if (stroke.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            if (pts.size == 1) {
                canvas.drawCircle(pts[0].x, pts[0].y, p.strokeWidth / 2f, p.apply { style = Paint.Style.FILL })
            } else {
                val path = android.graphics.Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                canvas.drawPath(path, p)
            }
        }
    }

    /** Decode file gambar secukupnya agar muat reqW x reqH (anti-OOM). */
    private fun decodeFileFit(path: String, reqW: Int, reqH: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    }.getOrNull()

    // ---- Translate (OkHttp yang sudah ada, key dari Settings) ----
    private fun providerFor(id: String): TranslationProvider = when (id) {
        "agnes" -> agnes
        "sumopod" -> sumopod
        else -> gemini
    }

    suspend fun translatePreview(
        text: String,
        providerId: String,
        targetLang: String,
    ): Result<String> = runCatching {
        providerFor(providerId).translate(text, targetLang)
    }

    fun applyTranslation(translated: String) {
        val active = activeTextLayer() ?: return
        commitLayers(
            LayerManager.updateText(_uiState.value.layers, active.id, translated, active.style)
        )
        toast("Terjemahan diterapkan ke layer teks aktif")
    }

    // ---- Project (Room -> state; gambar tetap dibaca sampled anti-OOM) ----
    fun loadProject(id: Long) {
        if (id <= 0) return
        val cur = _uiState.value
        if (cur.projectId == id && cur.sourceUri != null) return
        viewModelScope.launch {
            val e = dao.getById(id)
            if (e == null) {
                _events.emit(EditorEvent.Message("Project tidak ditemukan"))
                return@launch
            }
            _uiState.update {
                it.copy(
                    projectId = e.id, projectName = e.name, sourceUri = e.sourceUri,
                    imageWidth = e.width, imageHeight = e.height,
                )
            }
        }
    }

    private fun requireSource(): String? {
        val uri = _uiState.value.sourceUri
        if (uri == null) toast("Buka project dari Home dulu (butuh gambar sumber)")
        return uri
    }

    /** Decode sampled (sisi panjang dibatasi) agar tall-image tidak OOM. */
    private suspend fun workingBitmap(maxLongSide: Int): Bitmap? {
        val uriStr = requireSource() ?: return null
        return runCatching { tall.decodeSampled(Uri.parse(uriStr), maxLongSide) }.getOrNull()
    }

    // ---- Bubble detect (YOLOv8m comic-speech-bubble) ----
    // - Gambar biasa: 1 working bitmap 2400 + tiling 1200/300 di dalam detector.
    // - Gambar TALL (tinggi >3000px, mis. 720x16000): decode per STRIP vertikal
    //   di resolusi penuh via BitmapRegionDecoder (tanpa OOM) + overlap 200px,
    //   deteksi per strip, lalu NMS global ternormalisasi. Sampling full-image
    //   TIDAK dipakai di sini karena menghancurkan bubble kecil (720x16000 →
    //   108x2400).
    fun detectBubbles() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            setBusy("Detect bubble…")
            try {
                val uriStr = requireSource() ?: return@launch
                val uri = Uri.parse(uriStr)
                var fullW = _uiState.value.imageWidth
                var fullH = _uiState.value.imageHeight
                if (fullW <= 0 || fullH <= 0) {
                    runCatching { tall.probe(uri) }.getOrNull()?.let {
                        fullW = it.width; fullH = it.height
                    }
                }
                val norm = if (fullW > 0 && fullH > TALL_STRIP_THRESHOLD_H) {
                    detectTallStrips(uri, fullW, fullH)
                } else {
                    val work = workingBitmap(2400) ?: return@launch
                    try {
                        // Inferensi ORT di Default agar UI 60fps tidak drop (stabil).
                        val res = withContext(Dispatchers.Default) { detector.detect(work) }
                        val ww = res.width.toFloat()
                        val hh = res.height.toFloat()
                        res.bubbles.map { b ->
                            Bubble(
                                RectF(b.box.left / ww, b.box.top / hh, b.box.right / ww, b.box.bottom / hh),
                                b.score,
                            )
                        }
                    } finally {
                        if (!work.isRecycled) work.recycle()
                    }
                }
                _uiState.update { it.copy(bubbles = norm) }
                _events.emit(EditorEvent.Message("Bubble: ${norm.size} terdeteksi"))
            } catch (e: Exception) {
                _events.emit(EditorEvent.Message((e.message ?: "Detect gagal").take(250)))
            } finally {
                clearBusy()
            }
        }
    }

    /**
     * Deteksi gambar tall strip-per-strip di resolusi penuh (lebar ≤1200px).
     * Tiap strip: region-decode → YOLO (tiling internal 1200/300 tetap jalan) →
     * petakan ke koordinat ternormalisasi full-image. NMS global di akhir
     * menghapus duplikat area overlap.
     */
    private suspend fun detectTallStrips(uri: Uri, fullW: Int, fullH: Int): List<Bubble> {
        // Sample power-of-2 agar lebar strip ≤1200px (720 → sample 1 = full-res).
        var sample = 1
        while (fullW / sample > 1200) sample *= 2
        val stripH = 2000 * sample // px sumber per strip
        val overlap = 200 * sample // px sumber overlap antar-strip
        val found = mutableListOf<Pair<RectF, Float>>()
        // Hitung jumlah strip dulu untuk progress determinat.
        var total = 0
        var ty = 0
        while (ty < fullH) {
            total++
            val y1 = minOf(ty + stripH, fullH)
            if (y1 >= fullH) break
            ty = y1 - overlap
        }
        var y = 0
        var idx = 0
        while (y < fullH) {
            idx++
            val y1 = minOf(y + stripH, fullH)
            setBusy("Detect bubble strip $idx/$total…", idx.toFloat() / total.coerceAtLeast(1))
            val bmp = runCatching { tall.decodeRegion(uri, 0, y, fullW, y1, sample) }.getOrNull()
            if (bmp != null) {
                try {
                    val res = withContext(Dispatchers.Default) { detector.detect(bmp) }
                    val ww = res.width.toFloat()
                    val hh = res.height.toFloat()
                    val spanH = (y1 - y).toFloat()
                    res.bubbles.forEach { b ->
                        // Strip selebar full-image → fraksi x langsung; y via offset strip.
                        val l = (b.box.left / ww).coerceIn(0f, 1f)
                        val r = (b.box.right / ww).coerceIn(0f, 1f)
                        val t = ((y + (b.box.top / hh) * spanH) / fullH).coerceIn(0f, 1f)
                        val btm = ((y + (b.box.bottom / hh) * spanH) / fullH).coerceIn(0f, 1f)
                        if (r > l && btm > t) found += RectF(l, t, r, btm) to b.score
                    }
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }
            if (y1 >= fullH) break
            y = y1 - overlap
        }
        return nmsNormalized(found, IOU_TALL_MERGE)
    }

    /** NMS global untuk box ternormalisasi (gabungan strip / tile). */
    private fun nmsNormalized(boxes: List<Pair<RectF, Float>>, iouTh: Float): List<Bubble> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedByDescending { it.second }
        val kept = mutableListOf<Pair<RectF, Float>>()
        for (b in sorted) {
            if (kept.none { iouRect(it.first, b.first) > iouTh }) kept += b
        }
        return kept.map { Bubble(it.first, it.second) }
    }

    private fun iouRect(a: RectF, b: RectF): Float {
        val l = maxOf(a.left, b.left)
        val t = maxOf(a.top, b.top)
        val r = minOf(a.right, b.right)
        val bo = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, r - l) * maxOf(0f, bo - t)
        if (inter <= 0f) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    // ---- OCR (ML Kit dulu; region = seleksi/lasso bila ada) ----
    fun runOcr() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            setBusy("OCR…")
            try {
                val work = workingBitmap(2048) ?: return@launch
                val region = currentRegionPixels(work.width, work.height)
                val res = withContext(Dispatchers.Default) { ocr.recognize(work, region) }
                val ww = res.width.toFloat()
                val hh = res.height.toFloat()
                val norm = res.lines.map { l ->
                    l.copy(polygon = l.polygon.map { p -> PointF(p.x / ww, p.y / hh) })
                }
                if (!work.isRecycled) work.recycle()
                _uiState.update { it.copy(ocrLines = norm) }
                val preview = norm.firstOrNull()?.text?.take(80) ?: "-"
                _events.emit(EditorEvent.Message("OCR: ${norm.size} baris. 1: $preview"))
            } catch (e: Exception) {
                _events.emit(EditorEvent.Message((e.message ?: "OCR gagal").take(250)))
            } finally {
                clearBusy()
            }
        }
    }

    /** Region preseleksi dalam px bitmap kerja (seleksi rect / bbox lasso), null = full. */
    private fun currentRegionPixels(w: Int, h: Int): List<PointF>? {
        val s = _uiState.value
        val r = s.selectionRect
        if (r != null) {
            return listOf(
                PointF(r.left * w, r.top * h), PointF(r.right * w, r.top * h),
                PointF(r.right * w, r.bottom * h), PointF(r.left * w, r.bottom * h),
            )
        }
        if (s.lassoPoints.size >= 3) {
            val xs = s.lassoPoints.map { it.x * w }
            val ys = s.lassoPoints.map { it.y * h }
            val l = xs.min(); val t = ys.min(); val rr = xs.max(); val b = ys.max()
            return listOf(PointF(l, t), PointF(rr, t), PointF(rr, b), PointF(l, b))
        }
        return null
    }

    // ---- Inpaint (mask = manual lasso/ocr/seleksi/bubble > PP-OCR otomatis) ----
    fun runInpaint() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            setBusy("Inpaint…")
            try {
                val work = workingBitmap(1600) ?: return@launch
                val polysN = maskPolysNormalized()
                // Mask manual menang; bila kosong, PP-OCR det otomatis
                // (perilaku Manhwa-Translator: PP-OCR hanya untuk mask).
                // Region = bbox bubble bila ada, else full image.
                val mask: Bitmap
                val maskNote: String
                if (polysN.isNotEmpty()) {
                    val polysPx = polysN.map { poly ->
                        poly.map { p -> PointF(p.x * work.width, p.y * work.height) }
                    }
                    mask = withContext(Dispatchers.Default) {
                        MaskBuilder.fromPolygons(work.width, work.height, polysPx, dilatePx = 6f)
                    }
                    maskNote = "seleksi manual"
                } else {
                    val s = _uiState.value
                    val regionPx: List<PointF>? = if (s.bubbles.isNotEmpty()) {
                        val l = s.bubbles.minOf { it.box.left } * work.width
                        val t = s.bubbles.minOf { it.box.top } * work.height
                        val r = s.bubbles.maxOf { it.box.right } * work.width
                        val b = s.bubbles.maxOf { it.box.bottom } * work.height
                        listOf(PointF(l, t), PointF(r, t), PointF(r, b), PointF(l, b))
                    } else {
                        null
                    }
                    try {
                        val auto = withContext(Dispatchers.Default) { textMask.maskFor(work, regionPx) }
                        mask = auto.mask
                        maskNote = "PP-OCR otomatis (${auto.boxes.size} area)"
                    } catch (e: com.volxsy.vastypr.ml.models.MissingModelException) {
                        if (!work.isRecycled) work.recycle()
                        _events.emit(
                            EditorEvent.Message(
                                "Buat seleksi (lasso/rect) dulu, atau copy manual " +
                                    "PP-OCRv6_small_det.onnx agar mask otomatis aktif"
                            )
                        )
                        return@launch
                    }
                }
                val (backend, inpainter) = activeInpainter()
                val out = withContext(Dispatchers.Default) { inpainter.inpaint(work, mask) }
                if (!mask.isRecycled) mask.recycle()
                if (!work.isRecycled) work.recycle()
                // Simpan hasil ke cache + jadikan layer baru (visible di canvas + export).
                val outFile = File(appCtx.cacheDir, "inpaint_${System.currentTimeMillis()}.png")
                withContext(Dispatchers.IO) {
                    FileOutputStream(outFile).use { fos ->
                        out.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                }
                if (!out.isRecycled) out.recycle()
                val layer = LayerManager.addImageWithPath("Inpaint ${backend.id}", outFile.absolutePath)
                commitLayers(_uiState.value.layers + layer)
                setActive(layer.id)
                _events.emit(EditorEvent.Message("Inpaint (${backend.id}, $maskNote) selesai → layer baru"))
            } catch (e: Exception) {
                _events.emit(EditorEvent.Message((e.message ?: "Inpaint gagal").take(250)))
            } finally {
                clearBusy()
            }
        }
    }

    // ---- NEXT_STEP Prioritas 1: Auto-clean per-bubble (1 tombol, ala core.py) ----
    // Tiap bubble: crop -> mask PP-OCR (fallback rect bila model belum ada) ->
    // inpaint backend aktif -> paste-back ke bitmap kerja. Hemat RAM: 1 bitmap
    // kerja 1600 + recycle crop/mask/out per bubble. Progress per-bubble via
    // busyLabel/busyProgress agar UI tidak terasa hang.
    fun cleanAllBubbles() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            try {
                var boxes = _uiState.value.bubbles.map { it.box }
                if (boxes.isEmpty()) {
                    setBusy("Detect bubble…")
                    val work0 = workingBitmap(1600) ?: return@launch
                    val res = withContext(Dispatchers.Default) { detector.detect(work0) }
                    val ww0 = res.width.toFloat()
                    val hh0 = res.height.toFloat()
                    val norm = res.bubbles.map { b ->
                        Bubble(
                            RectF(
                                b.box.left / ww0, b.box.top / hh0,
                                b.box.right / ww0, b.box.bottom / hh0,
                            ),
                            b.score,
                        )
                    }
                    if (!work0.isRecycled) work0.recycle()
                    _uiState.update { it.copy(bubbles = norm) }
                    boxes = norm.map { it.box }
                    if (boxes.isEmpty()) {
                        _events.emit(EditorEvent.Message("Tidak ada bubble terdeteksi"))
                        return@launch
                    }
                }
                val work = workingBitmap(1600) ?: return@launch
                // Mutable copy untuk paste-back (copy bisa null bila OOM).
                val base: Bitmap = withContext(Dispatchers.Default) {
                    work.copy(Bitmap.Config.ARGB_8888, true)
                } ?: run {
                    if (!work.isRecycled) work.recycle()
                    _events.emit(EditorEvent.Message("Clean gagal: memori penuh"))
                    return@launch
                }
                if (!work.isRecycled) work.recycle()
                val (backend, inpainter) = activeInpainter()
                val canvas = Canvas(base)
                var ok = 0
                val total = boxes.size
                boxes.forEachIndexed { idx, nb ->
                    setBusy("Clean ${idx + 1}/$total…", if (total > 0) idx.toFloat() / total else null)
                    // NOTE: suspend call (mask/inpaint) tidak boleh di dalam runCatching
                    // biasa -> pakai try/catch manual + withContext(Default) agar stabil.
                    var cleanOne = false
                    try {
                        val pad = 0.004f
                        val l = ((nb.left - pad) * base.width).toInt().coerceIn(0, base.width - 1)
                        val t = ((nb.top - pad) * base.height).toInt().coerceIn(0, base.height - 1)
                        val r = ((nb.right + pad) * base.width).toInt().coerceIn(l + 1, base.width)
                        val b = ((nb.bottom + pad) * base.height).toInt().coerceIn(t + 1, base.height)
                        val bw = r - l
                        val bh = b - t
                        if (bw >= 8 && bh >= 8) {
                            val crop = Bitmap.createBitmap(base, l, t, bw, bh)
                            // Mask: PP-OCR dulu, fallback rect penuh bila model kosong/gagal.
                            var mask: Bitmap? = null
                            var maskOk = false
                            try {
                                val auto = withContext(Dispatchers.Default) { textMask.maskFor(crop, null) }
                                mask = auto.mask
                                maskOk = auto.boxes.isNotEmpty()
                                if (!maskOk && mask != null && !mask!!.isRecycled) mask!!.recycle()
                            } catch (_: Exception) {
                                maskOk = false
                            }
                            if (!maskOk) {
                                mask?.let { if (!it.isRecycled) it.recycle() }
                                mask = withContext(Dispatchers.Default) {
                                    MaskBuilder.fromPolygons(
                                        crop.width, crop.height,
                                        listOf(
                                            listOf(
                                                PointF(0f, 0f), PointF(crop.width.toFloat(), 0f),
                                                PointF(crop.width.toFloat(), crop.height.toFloat()),
                                                PointF(0f, crop.height.toFloat()),
                                            )
                                        ),
                                        dilatePx = 2f,
                                    )
                                }
                            }
                            val m = requireNotNull(mask)
                            val out = withContext(Dispatchers.Default) { inpainter.inpaint(crop, m) }
                            if (!m.isRecycled) m.recycle()
                            if (!crop.isRecycled) crop.recycle()
                            // Paste-back.
                            canvas.drawBitmap(out, l.toFloat(), t.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
                            if (!out.isRecycled) out.recycle()
                            cleanOne = true
                        }
                    } catch (_: Exception) {
                        cleanOne = false
                    }
                    if (cleanOne) ok++
                }
                setBusy("Menyimpan…", 1f)
                val outFile = File(appCtx.cacheDir, "clean_${System.currentTimeMillis()}.png")
                withContext(Dispatchers.IO) {
                    FileOutputStream(outFile).use { fos ->
                        base.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                }
                if (!base.isRecycled) base.recycle()
                val layer = LayerManager.addImageWithPath("Clean x$ok (${backend.id})", outFile.absolutePath)
                commitLayers(_uiState.value.layers + layer)
                setActive(layer.id)
                _events.emit(EditorEvent.Message("Clean selesai: $ok/$total bubble → layer baru"))
            } catch (e: Exception) {
                _events.emit(EditorEvent.Message((e.message ?: "Clean gagal").take(250)))
            } finally {
                clearBusy()
            }
        }
    }

    // ---- NEXT_STEP Prioritas 2: Render terjemahan ke bubble (fit_text sederhana) ----
    // Membuat Layer.Text baru di tengah bubble pertama (atau selection bila tak ada
    // bubble). Ukuran font di-fit agar muat: boxW_px / (0.55 * maxLineLen).
    fun renderTranslationToBubble(translated: String) {
        val text = translated.trim()
        if (text.isEmpty()) {
            toast("Terjemahan kosong")
            return
        }
        val s = _uiState.value
        val box: RectF? = s.bubbles.firstOrNull()?.box ?: s.selectionRect
        if (box == null) {
            // Tanpa acuan posisi: terapkan ke layer teks aktif seperti biasa.
            applyTranslation(text)
            return
        }
        val baseW = (s.imageWidth.takeIf { it > 0 } ?: s.canvasWidth.takeIf { it > 0 } ?: 1080).toFloat()
        val boxWpx = (box.right - box.left).coerceAtLeast(0.05f) * baseW
        val lines = text.split("\n")
        val maxLen = lines.maxOfOrNull { it.trim().length }?.coerceAtLeast(1) ?: 1
        val fitted = (boxWpx / (0.55f * maxLen)).coerceIn(14f, 52f)
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        val baseStyle = activeTextLayer()?.style ?: VastTextStyle()
        val style = baseStyle.copy(fontSizeSp = fitted, align = com.volxsy.vastypr.editor.model.VastAlign.CENTER)
        val layer = LayerManager.addText(text).copy(
            content = text,
            name = text.replace("\n", " ").take(16).ifBlank { "Bubble" },
            offsetX = (cx - 0.5f).coerceIn(-0.45f, 0.45f),
            offsetY = (cy - 0.5f).coerceIn(-0.45f, 0.45f),
            style = style,
        )
        commitLayers(s.layers + layer)
        setActive(layer.id)
        toast("Terjemahan dirender ke bubble (${fitted.toInt()}sp)")
    }

    /** Kandidat mask normalisasi 0..1: lasso > ocr > selectionRect > bubbles. */
    private fun maskPolysNormalized(): List<List<PointF>> {
        val s = _uiState.value
        if (s.lassoPoints.size >= 3) {
            return listOf(s.lassoPoints.map { PointF(it.x, it.y) })
        }
        if (s.ocrLines.isNotEmpty()) return s.ocrLines.map { it.polygon }
        s.selectionRect?.let { r ->
            return listOf(
                listOf(
                    PointF(r.left, r.top), PointF(r.right, r.top),
                    PointF(r.right, r.bottom), PointF(r.left, r.bottom),
                )
            )
        }
        if (s.bubbles.isNotEmpty()) {
            return s.bubbles.map { b ->
                listOf(
                    PointF(b.box.left, b.box.top), PointF(b.box.right, b.box.top),
                    PointF(b.box.right, b.box.bottom), PointF(b.box.left, b.box.bottom),
                )
            }
        }
        return emptyList()
    }

    fun toast(msg: String) = viewModelScope.launch { _events.emit(EditorEvent.Message(msg)) }

    // ---- Busy helper (fluid + stabil: 1 pekerjaan AI dalam satu waktu) ----
    private fun setBusy(label: String, progress: Float? = null) {
        _uiState.update { it.copy(isBusy = true, busyLabel = label, busyProgress = progress) }
    }

    private fun clearBusy() {
        _uiState.update { it.copy(isBusy = false, busyLabel = null, busyProgress = null) }
    }

    private suspend fun activeInpainter(): Pair<InpaintBackend, Inpainter> {
        val backend = InpaintBackend.fromId(prefs.inpaintBackend.first())
        val inpainter = when (backend) {
            InpaintBackend.LAMA -> lama
            InpaintBackend.MIGAN -> migan
            else -> telea
        }
        return backend to inpainter
    }

    companion object {
        /** Tinggi sumber di atas ini memakai jalur strip tall (mis. 720x16000). */
        const val TALL_STRIP_THRESHOLD_H = 3000
        /** IoU untuk NMS gabungan antar-strip tall. */
        const val IOU_TALL_MERGE = 0.45f
    }
}
