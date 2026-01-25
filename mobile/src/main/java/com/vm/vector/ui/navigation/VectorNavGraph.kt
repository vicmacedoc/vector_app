package com.vm.vector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vm.vector.ui.screens.AnalysisScreen
import com.vm.vector.ui.screens.CalendarScreen
import com.vm.vector.ui.screens.HomeScreen
import com.vm.vector.ui.screens.ListsScreen
import com.vm.vector.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    object Lists : Screen("lists")
    object Calendar : Screen("calendar")
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
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Analysis.route) {
            AnalysisScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
