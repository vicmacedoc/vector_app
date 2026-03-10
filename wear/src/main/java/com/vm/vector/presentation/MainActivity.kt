package com.vm.vector.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.vm.vector.data.WearMessageClient
import com.vm.core.wear.WorkoutCompletedPayload
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        val messageClient = WearMessageClient(this)

        setContent {
            VectorTheme {
                var navTarget by remember { mutableStateOf<WearNavTarget>(WearNavTarget.List) }
                var showSaveAlert by remember { mutableStateOf(false) }
                var sessions by remember { mutableStateOf<List<SessionItem>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
                var today by remember { mutableStateOf(dateFormat.format(Date())) }
                val scope = rememberCoroutineScope()

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

                LaunchedEffect(showSaveAlert) {
                    if (showSaveAlert) {
                        delay(2500)
                        showSaveAlert = false
                        navTarget = WearNavTarget.List
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    TimeText()
                    when (val target = navTarget) {
                        is WearNavTarget.List -> WorkoutListScreen(
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
                    if (showSaveAlert) {
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
