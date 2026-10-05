package com.cineenglish.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cineenglish.app.ui.screens.*

@Composable
fun CineNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Library.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Screen.SearchSubtitle.route) {
            SearchSubtitleScreen(
                onNavigateBack = { navController.popBackStack() },
                onMaterialImported = { materialId ->
                    navController.navigate(Screen.SentencePractice.createRoute(materialId)) {
                        popUpTo(Screen.Library.route)
                    }
                }
            )
        }

        composable(
            route = Screen.SentencePractice.route,
            arguments = listOf(navArgument("materialId") { type = NavType.LongType })
        ) { backStackEntry ->
            val materialId = backStackEntry.arguments?.getLong("materialId") ?: 0L
            SentencePracticeScreen(
                materialId = materialId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.VideoPractice.route,
            arguments = listOf(navArgument("materialId") { type = NavType.LongType })
        ) { backStackEntry ->
            val materialId = backStackEntry.arguments?.getLong("materialId") ?: 0L
            VideoPracticeScreen(
                materialId = materialId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ShadowRecall.route,
            arguments = listOf(navArgument("materialId") { type = NavType.LongType })
        ) { backStackEntry ->
            val materialId = backStackEntry.arguments?.getLong("materialId") ?: 0L
            ShadowRecallScreen(
                materialId = materialId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RolePlay.route,
            arguments = listOf(navArgument("materialId") { type = NavType.LongType })
        ) { backStackEntry ->
            val materialId = backStackEntry.arguments?.getLong("materialId") ?: 0L
            RolePlayScreen(
                materialId = materialId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Vocabulary.route) {
            VocabularyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Review.route) {
            ReviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LearningRecords.route) {
            LearningRecordsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
