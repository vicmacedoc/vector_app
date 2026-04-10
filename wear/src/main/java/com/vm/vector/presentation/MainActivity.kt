package com.vm.vector.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.vm.vector.data.WearMessageClient
import com.vm.core.wear.WorkoutCompletedPayload
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.SwipeToDismissValue
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import com.vm.core.wear.RoutineWearEntry
import com.vm.vector.presentation.routine.RoutineListScreen
import com.vm.vector.presentation.routine.RoutineTimerScreen
import com.vm.vector.presentation.workout.EnduranceFlowScreen
import com.vm.vector.presentation.theme.VectorTheme
import com.vm.vector.presentation.workout.ResistanceFlowScreen
import com.vm.vector.presentation.workout.SessionItem
import com.vm.vector.presentation.workout.WearNavTarget
import com.vm.vector.presentation.workout.WorkoutListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class WearAppRoot {
    MainMenu,
    Workout,
    Routine
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        val messageClient = WearMessageClient(this)

        setContent {
            VectorTheme {
                var appRoot by remember { mutableStateOf(WearAppRoot.MainMenu) }
                var navTarget by remember { mutableStateOf<WearNavTarget>(WearNavTarget.List) }
                var showSaveAlert by remember { mutableStateOf(false) }
                var showRoutineSaveAlert by remember { mutableStateOf(false) }
                var sessions by remember { mutableStateOf<List<SessionItem>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var routineEntries by remember { mutableStateOf<List<RoutineWearEntry>>(emptyList()) }
                var routineLoading by remember { mutableStateOf(false) }
                var routineError by remember { mutableStateOf<String?>(null) }
                var routineTimerEntry by remember { mutableStateOf<RoutineWearEntry?>(null) }
                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
                var today by remember { mutableStateOf(dateFormat.format(Date())) }
                val scope = rememberCoroutineScope()
                val activityContext = LocalContext.current
                DisposableEffect(navTarget, routineTimerEntry) {
                    val window = (activityContext as ComponentActivity).window
                    val keepScreenOn = routineTimerEntry != null ||
                        navTarget is WearNavTarget.ResistanceSession ||
                        navTarget is WearNavTarget.EnduranceSession
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                LaunchedEffect(Unit) {
                    messageClient.addListener()
                    messageClient.onWorkoutsResponse = { payload ->
                        val list = payload.sets
                            .groupBy { it.sessionTitle }
                            .map { entry ->
                                val firstDesc = entry.value.firstOrNull()?.description?.takeIf { it.isNotBlank() }
                                SessionItem(title = entry.key, sets = entry.value, description = firstDesc)
                            }
                        sessions = list
                        isLoading = false
                        error = null
                    }
                    messageClient.onRoutineResponse = { payload ->
                        routineEntries = payload.entries
                        routineLoading = false
                        routineError = null
                    }
                    isLoading = true
                    error = null
                    val ok = withContext(Dispatchers.IO) {
                        messageClient.requestWorkoutsForDate(today)
                    }
                    if (!ok) {
                        isLoading = false
                        error = "Phone not connected"
                    }
                }

                LaunchedEffect(appRoot, today, routineTimerEntry) {
                    if (appRoot != WearAppRoot.Routine || routineTimerEntry != null) return@LaunchedEffect
                    routineLoading = true
                    routineError = null
                    val ok = withContext(Dispatchers.IO) {
                        messageClient.requestRoutineForDate(today)
                    }
                    if (!ok) {
                        routineLoading = false
                        routineError = "Phone not connected"
                    }
                }

                LaunchedEffect(showSaveAlert) {
                    if (showSaveAlert) {
                        delay(2500)
                        showSaveAlert = false
                        navTarget = WearNavTarget.List
                    }
                }

                LaunchedEffect(showRoutineSaveAlert) {
                    if (showRoutineSaveAlert) {
                        delay(2500)
                        showRoutineSaveAlert = false
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    TimeText()
                    when (appRoot) {
                        WearAppRoot.MainMenu -> MainMenuScreen(
                            onWorkout = { appRoot = WearAppRoot.Workout },
                            onRoutine = {
                                appRoot = WearAppRoot.Routine
                                routineTimerEntry = null
                            }
                        )
                        WearAppRoot.Routine -> {
                            val timerEntry = routineTimerEntry
                            if (timerEntry != null) {
                                RoutineTimerScreen(
                                    date = today,
                                    entry = timerEntry,
                                    messageClient = messageClient,
                                    onRoutineSaved = {
                                        routineTimerEntry = null
                                        showRoutineSaveAlert = true
                                    },
                                    onExitDiscard = { routineTimerEntry = null }
                                )
                            } else {
                                val routineDismissState = rememberSwipeToDismissBoxState()
                                LaunchedEffect(routineDismissState.currentValue) {
                                    if (routineDismissState.currentValue == SwipeToDismissValue.Dismissed) {
                                        appRoot = WearAppRoot.MainMenu
                                    }
                                }
                                SwipeToDismissBox(
                                    state = routineDismissState,
                                    modifier = Modifier.fillMaxSize()
                                ) { isBackground ->
                                    if (!isBackground) {
                                        RoutineListScreen(
                                            date = today,
                                            entries = routineEntries,
                                            isLoading = routineLoading,
                                            error = routineError,
                                            onEntryClick = { routineTimerEntry = it },
                                            onRefresh = {
                                                today = dateFormat.format(Date())
                                                routineLoading = true
                                                routineError = null
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        messageClient.requestRoutineForDate(today)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        WearAppRoot.Workout -> when (val target = navTarget) {
                        is WearNavTarget.List -> {
                            val workoutDismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(workoutDismissState.currentValue) {
                                if (workoutDismissState.currentValue == SwipeToDismissValue.Dismissed) {
                                    appRoot = WearAppRoot.MainMenu
                                }
                            }
                            SwipeToDismissBox(
                                state = workoutDismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    WorkoutListScreen(
                                        date = today,
                                        sessions = sessions,
                                        isLoading = isLoading,
                                        error = error,
                                        onSessionClick = { session ->
                                            navTarget = if (session.isResistance) {
                                                WearNavTarget.ResistanceSession(session)
                                            } else {
                                                WearNavTarget.EnduranceSession(session)
                                            }
                                        },
                                        onRefresh = {
                                            today = dateFormat.format(Date())
                                            isLoading = true
                                            error = null
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    messageClient.requestWorkoutsForDate(today)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        is WearNavTarget.EnduranceSession -> {
                            var workoutStarted by remember(target) { mutableStateOf(false) }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmStateChange = { newValue ->
                                    if (newValue == androidx.wear.compose.material.SwipeToDismissValue.Dismissed) {
                                        !workoutStarted
                                    } else true
                                }
                            )
                            LaunchedEffect(dismissState.currentValue, workoutStarted) {
                                if (dismissState.currentValue == androidx.wear.compose.material.SwipeToDismissValue.Dismissed && !workoutStarted) {
                                    navTarget = WearNavTarget.List
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    EnduranceFlowScreen(
                                    session = target.session,
                                    date = today,
                                    onSave = { sets ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                messageClient.sendWorkoutCompleted(WorkoutCompletedPayload(today, sets))
                                            }
                                            navTarget = WearNavTarget.List
                                            showSaveAlert = true
                                        }
                                    },
                                    onBack = { navTarget = WearNavTarget.List },
                                    onWorkoutStarted = { workoutStarted = true }
                                )
                                }
                            }
                        }
                        is WearNavTarget.ResistanceSession -> {
                            var workoutStarted by remember(target) { mutableStateOf(false) }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmStateChange = { newValue ->
                                    if (newValue == androidx.wear.compose.material.SwipeToDismissValue.Dismissed) {
                                        !workoutStarted
                                    } else true
                                }
                            )
                            LaunchedEffect(dismissState.currentValue, workoutStarted) {
                                if (dismissState.currentValue == androidx.wear.compose.material.SwipeToDismissValue.Dismissed && !workoutStarted) {
                                    navTarget = WearNavTarget.List
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    ResistanceFlowScreen(
                                    session = target.session,
                                    date = today,
                                    onSave = { sets ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                messageClient.sendWorkoutCompleted(WorkoutCompletedPayload(today, sets))
                                            }
                                            showSaveAlert = true
                                        }
                                    },
                                    onBack = { navTarget = WearNavTarget.List },
                                    onWorkoutStarted = { workoutStarted = true }
                                )
                                }
                            }
                        }
                        }
                    }
                    if (showSaveAlert || showRoutineSaveAlert) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.background)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Data sent to phone",
                                style = MaterialTheme.typography.title3,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
