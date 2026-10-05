package com.cineenglish.app.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object SearchSubtitle : Screen("search_subtitle")
    object SentencePractice : Screen("sentence_practice/{materialId}") {
        fun createRoute(materialId: Long) = "sentence_practice/$materialId"
    }
    object VideoPractice : Screen("video_practice/{materialId}") {
        fun createRoute(materialId: Long) = "video_practice/$materialId"
    }
    object ShadowRecall : Screen("shadow_recall/{materialId}") {
        fun createRoute(materialId: Long) = "shadow_recall/$materialId"
    }
    object RolePlay : Screen("role_play/{materialId}") {
        fun createRoute(materialId: Long) = "role_play/$materialId"
    }
    object Vocabulary : Screen("vocabulary")
    object Review : Screen("review")
    object LearningRecords : Screen("learning_records")
    object Settings : Screen("settings")
}
