package com.volxsy.vastypr.editor.engine

// Skill: android-kotlin-core + android-state-management
// Undo/Redo generik dengan Command pattern — snapshot layers (immutable) agar O(1) mental model.
// Batas 100 langkah agar aman di RAM HP Android 8.
class UndoRedoManager<T>(private val maxSteps: Int = 100) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    fun push(state: T) {
        undoStack.addLast(state)
        if (undoStack.size > maxSteps) undoStack.removeFirst()
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.size > 1
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** Undo: kembalikan state sebelumnya, simpan current ke redo. */
    fun undo(current: T): T? {
        if (!canUndo()) return null
        redoStack.addLast(current)
        undoStack.removeLast() // buang current
        return undoStack.lastOrNull()
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(next)
        return next
    }

    fun reset(initial: T) {
        undoStack.clear(); redoStack.clear()
        undoStack.addLast(initial)
    }
}
