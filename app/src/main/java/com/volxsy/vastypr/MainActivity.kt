package com.volxsy.vastypr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.volxsy.vastypr.navigation.VastypRRoute
import com.volxsy.vastypr.ui.editor.EditorScreen
import com.volxsy.vastypr.ui.home.HomeScreen
import com.volxsy.vastypr.ui.models.ModelScreen
import com.volxsy.vastypr.ui.settings.SettingsScreen
import com.volxsy.vastypr.ui.theme.VastypRTheme
import dagger.hilt.android.AndroidEntryPoint

// Skill: android-compose-foundations + android-mobile-frontend-design (create mode)
// + android-compose-accessibility + android-state-management
// Nav sederhana tanpa NavHost untuk MVP-1 (0 dep tambahan, aman API 26).
// Naik ke navigation-compose saatambah deep-link (MVP-3).
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VastypRTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var route: VastypRRoute by remember { mutableStateOf(VastypRRoute.Home) }
                    // Ingat asal Models agar tombol Back kembali ke Editor bila dibuka dari Inpaint.
                    var modelsReturn: VastypRRoute by remember { mutableStateOf(VastypRRoute.Settings) }
                    when (val r = route) {
                        is VastypRRoute.Home -> HomeScreen(
                            onOpenEditor = { route = VastypRRoute.Editor(it) },
                            onOpenSettings = { route = VastypRRoute.Settings },
                        )
                        is VastypRRoute.Editor -> EditorScreen(
                            projectId = r.projectId,
                            onBack = { route = VastypRRoute.Home },
                            onOpenModels = {
                                modelsReturn = r
                                route = VastypRRoute.Models
                            },
                        )
                        is VastypRRoute.Settings -> SettingsScreen(
                            onBack = { route = VastypRRoute.Home },
                            onOpenModels = {
                                modelsReturn = VastypRRoute.Settings
                                route = VastypRRoute.Models
                            },
                        )
                        is VastypRRoute.Models -> ModelScreen(
                            onBack = { route = modelsReturn },
                        )
                    }
                }
            }
        }
    }
}
