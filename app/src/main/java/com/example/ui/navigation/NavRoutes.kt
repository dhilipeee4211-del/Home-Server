package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Files : Screen("files")
    data object Media : Screen("media")
    data object MediaDetails : Screen("media_details/{mediaId}") {
        fun createRoute(mediaId: String) = "media_details/$mediaId"
    }
    data object Admin : Screen("admin")
    data object Settings : Screen("settings")
}
