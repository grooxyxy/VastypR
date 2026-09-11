package com.volxsy.vastypr.navigation

// Skill: android-kotlin-core — route boring & eksplisit, tanpa string tersebar.
// Handoff: android-navigation-deeplinks saat butuh app-links (MVP-3).
sealed interface VastypRRoute {
    data object Home : VastypRRoute
    data class Editor(val projectId: Long) : VastypRRoute
    data object Settings : VastypRRoute
    data object Models : VastypRRoute
}
