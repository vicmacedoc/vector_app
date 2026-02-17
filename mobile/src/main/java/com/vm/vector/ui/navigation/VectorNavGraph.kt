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
import com.vm.vector.ui.screens.ListsScreen
import com.vm.vector.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    object Lists : Screen("lists")
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
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Lists.route) {
            ListsScreen()
        }
        composable(Screen.Calendar.route) {
            CalendarScreen()
        }
        composable(
            route = Screen.CalendarWithDate.route,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            val category = backStackEntry.arguments?.getString("category") ?: "Diet"
            CalendarScreen(initialDate = date, initialCategory = category)
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCalendar = { date, category ->
                    navController.navigate(Screen.CalendarWithDate.route(date, category)) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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
