package com.volxsy.vastypr.editor.engine

import com.volxsy.vastypr.editor.model.Layer
import com.volxsy.vastypr.editor.model.VastTextStyle
import com.volxsy.vastypr.editor.model.withClip
import com.volxsy.vastypr.editor.model.withName
import com.volxsy.vastypr.editor.model.withOpacity
import com.volxsy.vastypr.editor.model.withVisible
import java.util.UUID

// Skill: android-architecture-clean — pure Kotlin, tanpa dependensi Android/Compose.
// Semua operasi layer: add / delete / duplicate / copy / clip / folder / reorder.
// Dipakai EditorViewModel + diuji unit (lihat skill android-testing-unit).
object LayerManager {

    fun addImage(name: String = "Image"): List<Layer> = listOf(
        Layer.Image(id = UUID.randomUUID().toString(), name = name)
    )

    /** Image layer yang menunjuk ke file bitmap (hasil inpaint / salinan kerja). */
    fun addImageWithPath(name: String, path: String): Layer.Image =
        Layer.Image(id = UUID.randomUUID().toString(), name = name, bitmapPath = path)

    fun addText(content: String = "New text"): Layer.Text =
        Layer.Text(id = UUID.randomUUID().toString(), name = content.take(16), content = content)

    fun addFolder(name: String = "Group"): Layer.Folder =
        Layer.Folder(id = UUID.randomUUID().toString(), name = name)

    fun delete(layers: List<Layer>, id: String): List<Layer> =
        layers.filterNot { it.id == id }.map {
            if (it is Layer.Folder) it.copy(children = delete(it.children, id)) else it
        }

    fun duplicate(layers: List<Layer>, id: String): List<Layer> {
        val out = mutableListOf<Layer>()
        layers.forEach { l ->
            out += l
            if (l.id == id) out += copyWithNewId(l)
        }
        // duplicate di dalam folder
        return out.map {
            if (it is Layer.Folder) it.copy(children = duplicate(it.children, id)) else it
        }
    }

    fun toggleVisible(layers: List<Layer>, id: String): List<Layer> = map(layers, id) {
        it.withVisible(!it.visible)
    }

    fun setOpacity(layers: List<Layer>, id: String, opacity: Float): List<Layer> =
        map(layers, id) { it.withOpacity(opacity) }

    fun setClip(layers: List<Layer>, id: String, clip: Boolean): List<Layer> =
        map(layers, id) { it.withClip(clip) }

    fun rename(layers: List<Layer>, id: String, name: String): List<Layer> =
        map(layers, id) { it.withName(name) }

    fun updateText(layers: List<Layer>, id: String, content: String, style: VastTextStyle): List<Layer> =
        map(layers, id) {
            if (it is Layer.Text) it.copy(content = content, style = style, name = content.take(16)) else it
        }

    fun move(layers: List<Layer>, from: Int, to: Int): List<Layer> {
        if (from !in layers.indices || to !in layers.indices) return layers
        val m = layers.toMutableList()
        val item = m.removeAt(from)
        m.add(to, item)
        return m
    }

    private fun map(layers: List<Layer>, id: String, f: (Layer) -> Layer): List<Layer> =
        layers.map {
            var cur = if (it.id == id) f(it) else it
            if (cur is Layer.Folder) cur = cur.copy(children = map(cur.children, id, f))
            cur
        }

    private fun copyWithNewId(l: Layer): Layer {
        val nid = UUID.randomUUID().toString()
        return when (l) {
            is Layer.Image -> l.copy(id = nid, name = l.name + " copy")
            is Layer.Text -> l.copy(id = nid, name = l.name + " copy")
            is Layer.Folder -> l.copy(
                id = nid, name = l.name + " copy",
                children = l.children.map { copyWithNewId(it) }
            )
        }
    }
}
