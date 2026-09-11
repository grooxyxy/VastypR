package com.volxsy.vastypr.editor.engine

import com.volxsy.vastypr.editor.model.Layer
import org.junit.Assert.*
import org.junit.Test

// Skill: android-testing-unit — fast, focused, tanpa Android framework.
class LayerManagerTest {

    @Test fun addDelete() {
        val layers = LayerManager.addImage("BG")
        assertEquals(1, layers.size)
        val deleted = LayerManager.delete(layers, layers.first().id)
        assertTrue(deleted.isEmpty())
    }

    @Test fun duplicateKeepsBothWithNewId() {
        val orig = Layer.Text(id = "t1", name = "Hi", content = "Hi")
        val dup = LayerManager.duplicate(listOf(orig), "t1")
        assertEquals(2, dup.size)
        assertNotEquals(dup[0].id, dup[1].id)
    }

    @Test fun toggleVisibleAndOpacity() {
        val orig = Layer.Text(id = "t1", name = "Hi", content = "Hi")
        val hidden = LayerManager.toggleVisible(listOf(orig), "t1").first()
        assertFalse(hidden.visible)
        val faded = LayerManager.setOpacity(listOf(orig), "t1", 0.5f).first()
        assertEquals(0.5f, faded.opacity, 0.001f)
    }

    @Test fun clipAndMove() {
        val a = Layer.Text(id = "a", name = "A", content = "A")
        val b = Layer.Text(id = "b", name = "B", content = "B")
        val clipped = LayerManager.setClip(listOf(a, b), "b", true).last()
        assertTrue(clipped.clipToBelow)
        val moved = LayerManager.move(listOf(a, b), 0, 1)
        assertEquals("b", moved[0].id)
    }
}
