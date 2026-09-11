package com.volxsy.vastypr.editor.model

// Skill: android-state-management — tool sebagai state eksplisit, bukan boolean tersebar.
enum class EditorTool {
    PAN,        // pan tools (geser canvas tall)
    MOVE,       // pindah layer aktif
    SELECT_RECT,// selection tools: kotak
    SELECT_LASSO, // selection tools: bebas (MVP-2: path)
    BRUSH,      // brush + eraser via mode
    EYEDROP,    // eyedrop (ambil warna dari canvas)
    TEXT,       // add text
    CROP,       // crop image
}

enum class BrushMode { PAINT, ERASE }
