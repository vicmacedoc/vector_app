package com.vm.vector.presentation

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.SwipeToDismissValue
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import com.vm.core.models.WorkoutSet
import com.vm.core.wear.ActivitiesForWearPayload
import com.vm.core.wear.ActivityCompletionSetPayload
import com.vm.core.wear.FoodDayForWearPayload
import com.vm.core.wear.FoodStatusSetPayload
import com.vm.core.wear.MotionCompletePayload
import com.vm.core.wear.SupermarketForWearPayload
import com.vm.core.wear.SupermarketTogglePayload
import com.vm.core.wear.WearActivityCompletion
import com.vm.core.wear.WearFoodState
import com.vm.core.wear.WearMotionBucket
import com.vm.core.wear.WearMotionTask
import com.vm.core.wear.WorkoutCompletedPayload
import com.vm.vector.data.WearAudioSender
import com.vm.vector.data.WearMessageClient
import com.vm.vector.presentation.motion.MotionScreen
import com.vm.vector.presentation.nutrition.NutritionScreen
import com.vm.vector.presentation.routine.RoutineDayScreen
import com.vm.vector.presentation.routine.nextOnWatch
import com.vm.vector.presentation.supermarket.SupermarketScreen
import com.vm.vector.presentation.theme.VectorTheme
import com.vm.vector.presentation.voice.VoiceCaptureScreen
import com.vm.vector.presentation.workout.IntervalFlowScreen
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
    Motion,
    Routine,
    Nutrition,
    Supermarket,
    Voice
}

private val SessionOrdinalRegex = Regex("^ex_(?:plan_)?(\\d+)_(\\d+)$")
private val AddedSessionOrdinalRegex = Regex("^ex_added_(\\d+)_(\\d+)$")

/**
 * Preset ids are `ex_<sessionIndex>_<exerciseIndex>` (`ex_plan_…` for a re-planned day); splitting by this
 * avoids merging two same-titled sessions.
 */
private fun sessionOrdinalFromExerciseId(exerciseId: String): Int? =
    SessionOrdinalRegex.matchEntire(exerciseId)?.groupValues?.get(1)?.toIntOrNull()
        ?: AddedSessionOrdinalRegex.matchEntire(exerciseId)?.let { 1000 + it.groupValues[1].toInt() }

/**
 * Preserves phone ordering. Prefer splitting by session index from [WorkoutSet.exerciseId]; fall back to
 * consecutive runs with the same [WorkoutSet.sessionTitle] for ad-hoc / legacy ids.
 */
private fun sessionsFromWorkoutPayload(sets: List<WorkoutSet>): List<SessionItem> {
    if (sets.isEmpty()) return emptyList()
    val out = mutableListOf<SessionItem>()
    var i = 0
    while (i < sets.size) {
        val chunk = mutableListOf<WorkoutSet>()
        val ord = sessionOrdinalFromExerciseId(sets[i].exerciseId)
        if (ord != null) {
            while (i < sets.size && sessionOrdinalFromExerciseId(sets[i].exerciseId) == ord) {
                chunk.add(sets[i])
                i++
            }
        } else {
            val title = sets[i].sessionTitle
            while (i < sets.size && sets[i].sessionTitle == title) {
                chunk.add(sets[i])
                i++
            }
        }
        val first = chunk.first()
        val desc = first.sessionDescription.takeIf { it.isNotBlank() }
            ?: chunk.firstOrNull()?.description?.takeIf { it.isNotBlank() }
        out.add(SessionItem(title = first.sessionTitle, sets = chunk, description = desc))
    }
    return out
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        val messageClient = WearMessageClient(this)
        val audioSender = WearAudioSender(this)
        val hasMicrophone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

        setContent {
            VectorTheme {
                var appRoot by remember { mutableStateOf(WearAppRoot.MainMenu) }
                var navTarget by remember { mutableStateOf<WearNavTarget>(WearNavTarget.List) }
                var showSaveAlert by remember { mutableStateOf(false) }
                var sessions by remember { mutableStateOf<List<SessionItem>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var motionTasks by remember { mutableStateOf<List<WearMotionTask>>(emptyList()) }
                var motionLoading by remember { mutableStateOf(false) }
                var motionError by remember { mutableStateOf<String?>(null) }
                var completingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                var foodPayload by remember { mutableStateOf<FoodDayForWearPayload?>(null) }
                var foodLoading by remember { mutableStateOf(false) }
                var foodError by remember { mutableStateOf<String?>(null) }
                var activitiesPayload by remember { mutableStateOf<ActivitiesForWearPayload?>(null) }
                var activitiesLoading by remember { mutableStateOf(false) }
                var activitiesError by remember { mutableStateOf<String?>(null) }
                var supermarketPayload by remember { mutableStateOf<SupermarketForWearPayload?>(null) }
                var supermarketLoading by remember { mutableStateOf(false) }
                var supermarketError by remember { mutableStateOf<String?>(null) }
                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
                val displayDateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.US) }
                var today by remember { mutableStateOf(dateFormat.format(Date())) }
                val dateLabel = remember(today) {
                    try {
                        displayDateFormat.format(dateFormat.parse(today) ?: Date())
                    } catch (_: Exception) {
                        today
                    }
                }
                val scope = rememberCoroutineScope()
                val activityContext = LocalContext.current
                val activityWindow = (activityContext as ComponentActivity).window
                LaunchedEffect(navTarget) {
                    // Defer window flag changes so they don't force a remeasure while
                    // SwipeToDismissBox content is still attaching/detaching.
                    delay(1)
                    val keepScreenOn =
                        navTarget is WearNavTarget.ResistanceSession ||
                            navTarget is WearNavTarget.IntervalSession
                    if (keepScreenOn) {
                        activityWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        activityWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                DisposableEffect(Unit) {
                    onDispose {
                        activityWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                LaunchedEffect(Unit) {
                    messageClient.addListener()
                    messageClient.onWorkoutsResponse = { payload ->
                        sessions = sessionsFromWorkoutPayload(payload.sets)
                        isLoading = false
                        error = null
                    }
                    messageClient.onMotionResponse = { payload ->
                        motionTasks = payload.tasks
                        completingIds = emptySet()
                        motionLoading = false
                        motionError = null
                    }
                    messageClient.onFoodResponse = { payload ->
                        foodPayload = payload
                        foodLoading = false
                        foodError = null
                    }
                    messageClient.onActivitiesResponse = { payload ->
                        activitiesPayload = payload
                        activitiesLoading = false
                        activitiesError = null
                    }
                    messageClient.onSupermarketResponse = { payload ->
                        supermarketPayload = payload
                        supermarketLoading = false
                        supermarketError = null
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
                    // Prefetch for menu subtitles; flush queued voice notes.
                    withContext(Dispatchers.IO) {
                        messageClient.requestMotion()
                        messageClient.requestActivitiesForDate(today)
                        messageClient.requestFoodForDate(today)
                        messageClient.requestSupermarket()
                        audioSender.flushOutbox()
                    }
                }

                LaunchedEffect(showSaveAlert) {
                    if (showSaveAlert) {
                        delay(2500)
                        showSaveAlert = false
                        navTarget = WearNavTarget.List
                    }
                }

                LaunchedEffect(appRoot) {
                    when (appRoot) {
                        WearAppRoot.Motion -> {
                            motionLoading = true
                            motionError = null
                            val ok = withContext(Dispatchers.IO) { messageClient.requestMotion() }
                            if (!ok) {
                                motionLoading = false
                                motionError = "Phone not connected"
                            }
                        }
                        WearAppRoot.Routine -> {
                            activitiesLoading = true
                            activitiesError = null
                            today = dateFormat.format(Date())
                            val ok = withContext(Dispatchers.IO) {
                                messageClient.requestActivitiesForDate(today)
                            }
                            if (!ok) {
                                activitiesLoading = false
                                activitiesError = "Phone not connected"
                            }
                        }
                        WearAppRoot.Nutrition -> {
                            foodLoading = true
                            foodError = null
                            today = dateFormat.format(Date())
                            val ok = withContext(Dispatchers.IO) {
                                messageClient.requestFoodForDate(today)
                            }
                            if (!ok) {
                                foodLoading = false
                                foodError = "Phone not connected"
                            }
                        }
                        WearAppRoot.Supermarket -> {
                            supermarketLoading = true
                            supermarketError = null
                            val ok = withContext(Dispatchers.IO) { messageClient.requestSupermarket() }
                            if (!ok) {
                                supermarketLoading = false
                                supermarketError = "Phone not connected"
                            }
                        }
                        else -> Unit
                    }
                }

                val workoutSubtitle = when {
                    isLoading -> "Loading…"
                    error != null -> "Phone offline"
                    sessions.isEmpty() -> "No sessions"
                    else -> "${sessions.size} session${if (sessions.size == 1) "" else "s"} today"
                }
                val overdueCount = motionTasks.count { it.bucket == WearMotionBucket.OVERDUE }
                val motionSubtitle = when {
                    motionTasks.isEmpty() && motionError != null -> "Phone offline"
                    motionTasks.isEmpty() -> "No open tasks"
                    overdueCount > 0 -> "${motionTasks.size} open, $overdueCount overdue"
                    else -> "${motionTasks.size} open"
                }
                val foodItems = foodPayload?.items.orEmpty()
                val eatenCount = foodItems.count { it.state == WearFoodState.EATEN }
                val nutritionSubtitle = when {
                    foodError != null && foodPayload == null -> "Phone offline"
                    foodItems.isEmpty() -> "No meals"
                    else -> {
                        val kcal = foodItems.filter { it.state == WearFoodState.EATEN }.sumOf { it.kcal }.toInt()
                        "$eatenCount/${foodItems.size} eaten · ${kcal} kcal"
                    }
                }
                val routineItems = activitiesPayload?.activities.orEmpty().filter { !it.isSleep }
                val routineSubtitle = when {
                    activitiesError != null && activitiesPayload == null -> "Phone offline"
                    routineItems.isEmpty() -> "No activities"
                    else -> {
                        val done = routineItems.count { it.completion == WearActivityCompletion.DONE }
                        "$done/${routineItems.size} done"
                    }
                }
                val cartItems = supermarketPayload?.items.orEmpty()
                val supermarketSubtitle = when {
                    supermarketError != null && supermarketPayload == null -> "Phone offline"
                    supermarketPayload?.available == false -> "No list on phone"
                    cartItems.isEmpty() -> "Nothing to buy"
                    else -> "${cartItems.count { !it.isChecked }} to buy"
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    when (appRoot) {
                        WearAppRoot.MainMenu -> MainMenuScreen(
                            dateLabel = dateLabel,
                            motionSubtitle = motionSubtitle,
                            routineSubtitle = routineSubtitle,
                            nutritionSubtitle = nutritionSubtitle,
                            workoutSubtitle = workoutSubtitle,
                            showMemory = hasMicrophone,
                            onMemory = { appRoot = WearAppRoot.Voice },
                            onMotion = { appRoot = WearAppRoot.Motion },
                            onRoutine = { appRoot = WearAppRoot.Routine },
                            onNutrition = { appRoot = WearAppRoot.Nutrition },
                            onWorkout = { appRoot = WearAppRoot.Workout }
                        )
                        WearAppRoot.Routine -> {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                                    appRoot = WearAppRoot.MainMenu
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    RoutineDayScreen(
                                        payload = activitiesPayload,
                                        isLoading = activitiesLoading,
                                        error = activitiesError,
                                        onCycleCompletion = { activity ->
                                            val next = activity.completion.nextOnWatch()
                                            activitiesPayload = activitiesPayload?.let { p ->
                                                p.copy(
                                                    activities = p.activities.map {
                                                        if (it.id == activity.id) it.copy(completion = next) else it
                                                    }
                                                )
                                            }
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    messageClient.sendActivityCompletionSet(
                                                        ActivityCompletionSetPayload(today, activity.id, next)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        WearAppRoot.Supermarket -> {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                                    appRoot = WearAppRoot.Nutrition
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    SupermarketScreen(
                                        payload = supermarketPayload,
                                        isLoading = supermarketLoading,
                                        error = supermarketError,
                                        onToggle = { item ->
                                            val checked = !item.isChecked
                                            supermarketPayload = supermarketPayload?.let { p ->
                                                p.copy(
                                                    items = p.items.map {
                                                        if (it.index == item.index) it.copy(isChecked = checked) else it
                                                    }
                                                )
                                            }
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    messageClient.sendSupermarketToggle(
                                                        SupermarketTogglePayload(item.index, item.title, checked)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        WearAppRoot.Voice -> {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                                    appRoot = WearAppRoot.MainMenu
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    VoiceCaptureScreen(
                                        audioSender = audioSender,
                                        onDone = { appRoot = WearAppRoot.MainMenu }
                                    )
                                }
                            }
                        }
                        WearAppRoot.Motion -> {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                                    appRoot = WearAppRoot.MainMenu
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    MotionScreen(
                                        tasks = motionTasks,
                                        completingIds = completingIds,
                                        isLoading = motionLoading,
                                        error = motionError,
                                        onComplete = { task ->
                                            completingIds = completingIds + task.id
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    messageClient.sendMotionComplete(
                                                        MotionCompletePayload(task.id)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        WearAppRoot.Nutrition -> {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                                    appRoot = WearAppRoot.MainMenu
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxSize()
                            ) { isBackground ->
                                if (!isBackground) {
                                    NutritionScreen(
                                        payload = foodPayload,
                                        isLoading = foodLoading,
                                        error = foodError,
                                        onCycleStatus = { entry ->
                                            val next = when (entry.state) {
                                                WearFoodState.NONE -> WearFoodState.EATEN
                                                WearFoodState.EATEN -> WearFoodState.SKIPPED
                                                WearFoodState.SKIPPED -> WearFoodState.NONE
                                            }
                                            // Optimistic local update
                                            foodPayload = foodPayload?.copy(
                                                items = foodPayload!!.items.map {
                                                    if (it.id == entry.id) it.copy(state = next) else it
                                                }
                                            )
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    messageClient.sendFoodStatusSet(
                                                        FoodStatusSetPayload(today, entry.id, next)
                                                    )
                                                }
                                            }
                                        },
                                        supermarketSubtitle = supermarketSubtitle,
                                        onOpenSupermarket = { appRoot = WearAppRoot.Supermarket }
                                    )
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
                                            // Let the list SwipeToDismissBox / scroll dispose before
                                            // mounting the next screen (avoids LayoutNode owner crash).
                                            scope.launch {
                                                delay(32)
                                                navTarget = if (session.isResistance) {
                                                    WearNavTarget.ResistanceSession(session)
                                                } else {
                                                    WearNavTarget.IntervalSession(session)
                                                }
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
                        // No SwipeToDismissBox: nested dismiss + list scroll disposal races
                        // (LayoutNode owner crash). Explicit Back on the start screen is enough.
                        is WearNavTarget.IntervalSession -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                IntervalFlowScreen(
                                    session = target.session,
                                    onSave = { sets ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                messageClient.sendWorkoutCompleted(
                                                    WorkoutCompletedPayload(today, sets)
                                                )
                                            }
                                            navTarget = WearNavTarget.List
                                            showSaveAlert = true
                                        }
                                    },
                                    onBack = { navTarget = WearNavTarget.List },
                                    onWorkoutStarted = { }
                                )
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
