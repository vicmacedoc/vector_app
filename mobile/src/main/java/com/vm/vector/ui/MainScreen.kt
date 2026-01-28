package com.vm.vector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureWhite
import com.vm.vector.ui.navigation.Screen
import com.vm.vector.ui.navigation.VectorNavGraph

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = PureWhite,
        bottomBar = {
            Box(
                modifier = Modifier.height(64.dp)
            ) {
                NavigationBar(
                    containerColor = NavyDeep
                ) {
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                Icons.Filled.List, 
                                contentDescription = "Lists",
                                modifier = Modifier.size(30.dp)
                            ) 
                        },
                    selected = currentRoute == Screen.Lists.route,
                    onClick = {
                        navController.navigate(Screen.Lists.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        unselectedIconColor = PureWhite.copy(alpha = 0.6f),
                        indicatorColor = NavyDeep
                    )
                )
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                Icons.Filled.CalendarToday, 
                                contentDescription = "Calendar",
                                modifier = Modifier.size(30.dp)
                            ) 
                        },
                    selected = currentRoute == Screen.Calendar.route,
                    onClick = {
                        navController.navigate(Screen.Calendar.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        unselectedIconColor = PureWhite.copy(alpha = 0.6f),
                        indicatorColor = NavyDeep
                    )
                )
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                Icons.Filled.Home, 
                                contentDescription = "Home",
                                modifier = Modifier.size(30.dp)
                            ) 
                        },
                    selected = currentRoute == Screen.Home.route,
                    onClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        unselectedIconColor = PureWhite.copy(alpha = 0.6f),
                        indicatorColor = NavyDeep
                    )
                )
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                Icons.Filled.BarChart, 
                                contentDescription = "Analysis",
                                modifier = Modifier.size(30.dp)
                            ) 
                        },
                    selected = currentRoute == Screen.Analysis.route,
                    onClick = {
                        navController.navigate(Screen.Analysis.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        unselectedIconColor = PureWhite.copy(alpha = 0.6f),
                        indicatorColor = NavyDeep
                    )
                )
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                Icons.Filled.Settings, 
                                contentDescription = "Settings",
                                modifier = Modifier.size(30.dp)
                            ) 
                        },
                    selected = currentRoute == Screen.Settings.route,
                    onClick = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        unselectedIconColor = PureWhite.copy(alpha = 0.6f),
                        indicatorColor = NavyDeep
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        VectorNavGraph(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
