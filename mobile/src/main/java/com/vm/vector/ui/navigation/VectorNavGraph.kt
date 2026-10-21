package com.vm.vector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vm.vector.ui.screens.AnalysisScreen
import com.vm.vector.ui.screens.CalendarScreen
import com.vm.vector.ui.screens.HomeScreen
import com.vm.vector.ui.screens.SettingsScreen
import com.vm.vector.ui.screens.brain.AudioPlayerScreen
import com.vm.vector.ui.screens.brain.BrainScreen
import com.vm.vector.ui.screens.brain.LearnQuizScreen
import com.vm.vector.ui.screens.brain.LearningTileScreen
import com.vm.vector.ui.screens.brain.MemoryTextScreen
import com.vm.vector.ui.screens.brain.MemoryTileScreen
import com.vm.vector.ui.screens.brain.MotionListScreen
import com.vm.vector.ui.screens.brain.SupermarketScreen
import com.vm.vector.ui.viewmodel.BrainTab

sealed class Screen(val route: String) {
    object Brain : Screen("brain?tab={tab}") {
        const val HUB = "hub"
        fun route(tab: BrainTab? = null) =
            "brain?tab=${tab?.name ?: HUB}"
    }
    object BrainSupermarket : Screen("brain/supermarket")
    object BrainMotionList : Screen("brain/motion/{nodeId}") {
        fun route(nodeId: String) = "brain/motion/$nodeId"
    }
    object BrainMemory : Screen("brain/memory/{nodeId}") {
        fun route(nodeId: String) = "brain/memory/$nodeId"
    }
    object BrainTile : Screen("brain/tile/{tileId}") {
        fun route(tileId: String) = "brain/tile/$tileId"
    }
    object BrainMemoryText : Screen("brain/memory-text/{noteId}") {
        fun route(noteId: String) = "brain/memory-text/$noteId"
    }
    object BrainLearnQuiz : Screen("brain_learn_quiz/{nodeId}") {
        fun route(nodeId: String) = "brain_learn_quiz/$nodeId"
    }
    object BrainPlayer : Screen("brain/player/{assetId}") {
        fun route(assetId: String) = "brain/player/$assetId"
    }
    object Calendar : Screen("calendar")
    object CalendarWithDate : Screen("calendar/{date}/{category}") {
        fun route(date: String, category: String) = "calendar/$date/$category"
    }
    object Home : Screen("home")
    object Analysis : Screen("analysis")
    object Settings : Screen("settings")
}

@Composable
fun VectorNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(
            route = Screen.Brain.route,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = Screen.Brain.HUB
                },
            ),
        ) { backStackEntry ->
            val tabName = backStackEntry.arguments?.getString("tab").orEmpty()
            val tab = runCatching { BrainTab.valueOf(tabName) }.getOrNull()
            BrainScreen(
                initialTab = tab,
                onOpenSupermarket = { navController.navigate(Screen.BrainSupermarket.route) },
                onOpenTile = { tileId -> navController.navigate(Screen.BrainTile.route(tileId)) },
                onOpenMemory = { nodeId -> navController.navigate(Screen.BrainMemory.route(nodeId)) },
                onOpenPlayer = { assetId -> navController.navigate(Screen.BrainPlayer.route(assetId)) },
                onOpenMotionList = { nodeId -> navController.navigate(Screen.BrainMotionList.route(nodeId)) },
            )
        }
        composable(Screen.BrainSupermarket.route) {
            SupermarketScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.BrainMotionList.route,
            arguments = listOf(navArgument("nodeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val nodeId = backStackEntry.arguments?.getString("nodeId") ?: return@composable
            MotionListScreen(nodeId = nodeId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.BrainMemory.route,
            arguments = listOf(navArgument("nodeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val nodeId = backStackEntry.arguments?.getString("nodeId") ?: return@composable
            MemoryTileScreen(
                nodeId = nodeId,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { assetId -> navController.navigate(Screen.BrainPlayer.route(assetId)) },
                onOpenText = { noteId -> navController.navigate(Screen.BrainMemoryText.route(noteId)) },
            )
        }
        composable(
            route = Screen.BrainMemoryText.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
            MemoryTextScreen(
                noteId = noteId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.BrainTile.route,
            arguments = listOf(navArgument("tileId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tileId = backStackEntry.arguments?.getString("tileId") ?: return@composable
            LearningTileScreen(
                tileId = tileId,
                onBack = { navController.popBackStack() },
                onOpenText = { noteId -> navController.navigate(Screen.BrainMemoryText.route(noteId)) },
                onOpenPlayer = { assetId -> navController.navigate(Screen.BrainPlayer.route(assetId)) },
                onOpenTile = { nodeId -> navController.navigate(Screen.BrainTile.route(nodeId)) },
                onOpenQuiz = { nodeId -> navController.navigate(Screen.BrainLearnQuiz.route(nodeId)) },
            )
        }
        composable(
            route = Screen.BrainLearnQuiz.route,
            arguments = listOf(navArgument("nodeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val nodeId = backStackEntry.arguments?.getString("nodeId") ?: return@composable
            LearnQuizScreen(
                nodeId = nodeId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.BrainPlayer.route,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getString("assetId") ?: return@composable
            AudioPlayerScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(onNavigateToSettings = { navController.navigate(Screen.Settings.route) })
        }
        composable(
            route = Screen.CalendarWithDate.route,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            val category = backStackEntry.arguments?.getString("category") ?: "Food"
            CalendarScreen(
                initialDate = date,
                initialCategory = category,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCalendar = { date, category ->
                    navController.navigate(Screen.CalendarWithDate.route(date, category)) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToBrain = { tab ->
                    navController.navigate(Screen.Brain.route(tab)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(Screen.Analysis.route) {
            AnalysisScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
