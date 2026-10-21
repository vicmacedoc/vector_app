package com.vm.vector.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.vector.audio.VoiceCaptureController
import com.vm.vector.audio.VoiceCaptureState
import com.vm.vector.ui.components.rememberVoiceCaptureStarter
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.res.painterResource
import com.vm.vector.R
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.vm.core.ui.theme.PureBlack
import com.vm.vector.audio.BrainPlayerController
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
    val context = LocalContext.current
    val playerState by BrainPlayerController.state.collectAsState()
    val onPlayerScreen = currentRoute?.startsWith("brain/player") == true
    val showMiniPlayer = playerState.assetId != null && !onPlayerScreen
    val voiceState by VoiceCaptureController.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val startVoiceCapture = rememberVoiceCaptureStarter(
        onMicDenied = {
            Toast.makeText(context, "Microphone permission is needed for voice capture.", Toast.LENGTH_SHORT).show()
        },
    )

    Scaffold(
        containerColor = PureWhite,
        bottomBar = {
            Column {
                if (voiceState.isRecording) {
                    VoiceCaptureBar(
                        state = voiceState,
                        onTogglePause = { VoiceCaptureController.togglePause(context) },
                        onStop = { VoiceCaptureController.stop(context) },
                    )
                }
                if (showMiniPlayer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(NavyDeep.copy(alpha = 0.12f))
                            .clickable {
                                val id = playerState.assetId ?: return@clickable
                                navController.navigate(Screen.BrainPlayer.route(id)) {
                                    launchSingleTop = true
                                }
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = playerState.title.ifBlank { "Audio" },
                            color = PureBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { BrainPlayerController.togglePlayPause(context) },
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = NavyDeep,
                            )
                        }
                        IconButton(
                            onClick = { BrainPlayerController.close(context) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close player",
                                tint = NavyDeep,
                            )
                        }
                    }
                }
                val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                NavigationBar(
                    containerColor = NavyDeep,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.height(64.dp + navBarInset),
                ) {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_brain),
                                contentDescription = "Brain",
                                modifier = Modifier.size(30.dp),
                            )
                        },
                    selected = currentRoute?.startsWith("brain") == true,
                    onClick = {
                        navController.navigate(Screen.Brain.route()) {
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
                                Icons.Filled.EventAvailable,
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
                    modifier = Modifier.interceptLongPress {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (VoiceCaptureController.state.value.isRecording) {
                            VoiceCaptureController.stop(context)
                        } else {
                            startVoiceCapture()
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
        },
    ) { paddingValues ->
        VectorNavGraph(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun VoiceCaptureBar(
    state: VoiceCaptureState,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        nowElapsed = SystemClock.elapsedRealtime()
        while (!state.isPaused) {
            delay(500L)
            nowElapsed = SystemClock.elapsedRealtime()
        }
    }
    val totalSec = state.elapsedMs(nowElapsed) / 1000
    val elapsedLabel = "%d:%02d".format(totalSec / 60, totalSec % 60)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(NavyDeep.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (state.isPaused) IconGray else DeleteRed, CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (state.isPaused) "Paused · $elapsedLabel" else "Recording · $elapsedLabel",
            color = PureBlack,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onTogglePause) {
            Icon(
                imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (state.isPaused) "Resume recording" else "Pause recording",
                tint = NavyDeep,
            )
        }
        IconButton(onClick = onStop) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop and save to Brain Inbox",
                tint = NavyDeep,
            )
        }
    }
}

/**
 * Fires [onLongPress] after the long-press timeout and swallows the rest of the gesture,
 * so the wrapped item's own click never runs for that press.
 */
private fun Modifier.interceptLongPress(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            true
        }
        if (releasedEarly == null) {
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
