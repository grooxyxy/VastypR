package com.volxsy.vastypr.editor.io

import android.content.Context
import android.net.Uri
import com.volxsy.vastypr.data.local.ProjectDao
import com.volxsy.vastypr.data.local.ProjectEntity
import com.volxsy.vastypr.editor.engine.TallImageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Skill: android-media-files-sharing + android-permissions-activity-results
// Import memakai Photo Picker / SAF (ActivityResultContracts.PickVisualMedia / OpenDocument)
// sehingga minim permission. Fungsi ini hanya mencatat project ke Room setelah URI dipilih.
@Singleton
class ImportManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: ProjectDao,
    private val tall: TallImageManager,
) {
    suspend fun registerImported(uri: Uri, displayName: String): Long {
        val info = tall.probe(uri)
        // Persist URI permission agar tetap bisa dibuka setelah restart (SAF).
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { /* Photo Picker tidak perlu persist */ }
        return dao.insert(
            ProjectEntity(
                name = displayName.ifBlank { "Project ${System.currentTimeMillis()}" },
                width = info.width, height = info.height,
                sourceUri = uri.toString(),
            )
        )
    }
}
