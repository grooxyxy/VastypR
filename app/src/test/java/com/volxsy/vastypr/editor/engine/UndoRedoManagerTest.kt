package com.volxsy.vastypr.editor.engine

import org.junit.Assert.*
import org.junit.Test

// Skill: android-testing-unit
class UndoRedoManagerTest {

    @Test fun undoRedoRoundTrip() {
        val h = UndoRedoManager<List<String>>()
        h.reset(listOf("a"))
        h.push(listOf("a", "b"))
        assertTrue(h.canUndo())
        val undone = h.undo(listOf("a", "b"))
        assertEquals(listOf("a"), undone)
        assertTrue(h.canRedo())
        val redone = h.redo(listOf("a"))
        assertEquals(listOf("a", "b"), redone)
    }
}
