package com.volxsy.vastypr.editor.model

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.volxsy.vastypr.ml.detection.Bubble
import com.volxsy.vastypr.ml.detection.OcrLine

// Skill: android-state-management — 1 immutable UiState untuk seluruh editor.
// Loading/empty/error/offline diperlakukan first-class (lihat EditorScreen).
// Koordinat seleksi/crop/lasso/bubble/ocr DISIMPAN NORMALISASI 0..1 terhadap
// gambar sumber (bukan px canvas) agar tahan rotasi/resize/export.
// Mapping canvas<->normal memakai fit-rect (ContentScale.Fit) di VM/Screen.
data class EditorUiState(
    val projectId: Long = 0,
    val projectName: String = "Untitled",
    val sourceUri: String? = null, // gambar sumber project (content:// atau file://)
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val canvasWidth: Int = 0, // px Box canvas terakhir (untuk mapping export)
    val canvasHeight: Int = 0,
    val layers: List<Layer> = emptyList(),
    val activeLayerId: String? = null,
    val tool: EditorTool = EditorTool.PAN,
    val brushColor: Color = Color.White,
    val brushSizePx: Float = 24f,
    val brushMode: BrushMode = BrushMode.PAINT,
    val cropRect: RectF? = null, // tool CROP; diterapkan saat export (non-destruktif)
    val selectionRect: RectF? = null, // tool SELECT_RECT; bisa jadi mask inpaint
    val lassoPoints: List<Offset> = emptyList(), // tool SELECT_LASSO; bisa jadi mask inpaint
    val bubbles: List<Bubble> = emptyList(), // hasil deteksi YOLO (box normalisasi)
    val ocrLines: List<OcrLine> = emptyList(), // hasil OCR (polygon normalisasi)
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isBusy: Boolean = false, // export / decode region / ML berjalan
    // Label + progress determinat (0..1) untuk operasi AI. null = indeterminate/hidden.
    // Ditambah untuk fluid+stabil: cegah double-tap (UI disable saat isBusy) dan
    // tampilkan progres per-bubble saat Clean agar tidak terasa hang.
    val busyLabel: String? = null,
    val busyProgress: Float? = null,
    val message: String? = null, // one-shot via event, mirror ringan di state
)

sealed interface EditorEvent {
    data class Message(val text: String) : EditorEvent
    data class ExportDone(val uri: String) : EditorEvent
    data object RequestImport : EditorEvent
    data object RequestExport : EditorEvent
}
