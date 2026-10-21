package com.vm.vector.ui.screens.exercise

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vm.core.models.MuscleTargets
import com.vm.core.models.WorkoutDaySession
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.DropSetOrange
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.CalendarAiMode
import com.vm.vector.data.CalendarDayContext
import com.vm.vector.data.ExerciseAiContext
import com.vm.vector.data.ExerciseDropDraft
import com.vm.vector.data.ExerciseImportDefaults
import com.vm.vector.data.ExerciseImportDraft
import com.vm.vector.data.ExerciseImportStep
import com.vm.vector.data.ExerciseIntervalDraft
import com.vm.vector.data.ExerciseKind
import com.vm.vector.data.ExerciseSessionDraft
import com.vm.vector.data.ExerciseSetDraft
import com.vm.vector.data.GeminiExerciseClient
import com.vm.vector.data.defaultSessionTitle
import com.vm.vector.data.importSteps
import com.vm.vector.data.intervalMinutes
import com.vm.vector.data.normalizeMuscleTag
import com.vm.vector.data.normalizeRpe
import com.vm.vector.data.normalizedForImport
import com.vm.vector.data.toWorkoutDaySession
import com.vm.vector.data.validationError
import com.vm.vector.ui.components.DialogMultilineFieldDefaults
import com.vm.vector.ui.components.DiscardImportReviewDialog
import com.vm.vector.ui.components.ImportReviewFooter
import com.vm.vector.ui.components.KeyboardAwareDialogColumn
import com.vm.vector.ui.screens.calendar.ReplacesDayBanner
import com.vm.vector.ui.screens.calendar.calendarAiDialogTitle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Free-text entry point for AI exercise generation. Grounding context (muscle tags, load unit,
 * recent prescriptions) is fetched lazily on send and handed back so the wizard can reuse it.
 */
@Composable
fun FillExerciseFromAiDialog(
    date: String,
    contextProvider: suspend () -> ExerciseAiContext,
    mode: CalendarAiMode,
    onDismiss: () -> Unit,
    onDraftsReady: (List<ExerciseSessionDraft>, ExerciseAiContext) -> Unit,
    dayContextProvider: suspend () -> CalendarDayContext = { CalendarDayContext() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { GeminiExerciseClient(context.applicationContext) }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun send() {
        if (isLoading) return
        if (description.isBlank() && mode != CalendarAiMode.PLAN_PRESET) {
            errorMessage = "Describe the workout you want to add"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val aiContext = runCatching { contextProvider() }.getOrDefault(ExerciseAiContext())
            val dayContext = runCatching { dayContextProvider() }.getOrDefault(CalendarDayContext())
            val result = client.estimateSessions(
                mode = mode,
                description = description,
                date = date,
                context = aiContext,
                dayContext = dayContext,
            )
            isLoading = false
            result.onSuccess { drafts ->
                onDraftsReady(drafts, aiContext)
            }.onFailure { e ->
                errorMessage = e.message ?: "AI fill failed"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        modifier = Modifier.imePadding(),
        containerColor = PureWhite,
        title = { Text(calendarAiDialogTitle(mode), color = PureBlack) },
        text = {
            KeyboardAwareDialogColumn {
                Text(
                    when (mode) {
                        CalendarAiMode.SINGLE ->
                            "Describe one session for $date. Resistance (supersets and drop sets included), " +
                                "run / bike / swim intervals and other activities are all supported."
                        CalendarAiMode.MULTIPLE ->
                            "Describe the sessions to add on $date. Each session becomes its own tile."
                        CalendarAiMode.PLAN_SCRATCH ->
                            "Describe the whole training day for $date. The result replaces every session on the day."
                        CalendarAiMode.PLAN_PRESET ->
                            "Starts from this weekday's preset. Describe what to change, or leave it blank to " +
                                "keep the preset. The result replaces every session on the day."
                    },
                    color = SlateGray,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = DialogMultilineFieldDefaults.LargeMin,
                            max = DialogMultilineFieldDefaults.LargeMax,
                        ),
                    placeholder = {
                        Text(
                            "e.g. chest 4x8 with a drop on the last set, superset curls + pushdowns — " +
                                "or swim 8x100 yd Z2 on 20s rest — or tennis practice 90 min",
                            color = SlateGray,
                        )
                    },
                    enabled = !isLoading,
                    colors = exerciseImportFieldColors(),
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = DeleteRed, fontSize = 12.sp)
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = NavyDeep,
                            strokeWidth = 2.dp,
                        )
                        Text("Building the session…", color = SlateGray, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { send() },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyDeep,
                    contentColor = PureWhite,
                    disabledContainerColor = OffWhite,
                    disabledContentColor = SlateGray,
                ),
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = PureBlack)
            }
        },
    )
}

/**
 * Step-by-step review of generated sessions: one step per session tile, then one step per exercise
 * with its sets as subsections. Nothing reaches the calendar until every step validates.
 */
@Composable
fun ExerciseImportWizardDialog(
    initialSessions: List<ExerciseSessionDraft>,
    date: String,
    muscleVocabulary: List<String>,
    onDismiss: () -> Unit,
    onConfirmAll: (List<WorkoutDaySession>) -> Unit,
    sourceLabel: String = "AI",
    /** Plan modes: confirming replaces every session on the day. */
    replacesDay: Boolean = false,
) {
    val defaultLoadUnit = remember(initialSessions) {
        initialSessions.asSequence()
            .flatMap { it.exercises.asSequence() }
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.targetLoad != null }
            ?.unitLoad
            ?: ExerciseImportDefaults.LOAD_UNIT
    }
    var sessions by remember { mutableStateOf(initialSessions.normalizedForImport()) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var sessionForm by remember { mutableStateOf(SessionFormState()) }
    var exerciseForm by remember { mutableStateOf(ExerciseFormState()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val steps = sessions.importSteps()
    val step = steps.getOrNull(stepIndex)
    val currentSession = step?.let { sessions.getOrNull(it.sessionIndex) }

    fun loadFrom(list: List<ExerciseSessionDraft>, index: Int) {
        formError = null
        val target = list.importSteps().getOrNull(index) ?: return
        val session = list.getOrNull(target.sessionIndex) ?: return
        when (target) {
            is ExerciseImportStep.Session -> sessionForm = session.toForm()
            is ExerciseImportStep.Exercise -> {
                val exercise = session.exercises.getOrNull(target.exerciseIndex) ?: return
                exerciseForm = exercise.toForm(defaultLoadUnit)
            }
        }
    }

    fun snapshot(): List<ExerciseSessionDraft> {
        val target = step ?: return sessions
        val updated = sessions.toMutableList()
        val session = updated.getOrNull(target.sessionIndex) ?: return sessions
        when (target) {
            is ExerciseImportStep.Session -> {
                updated[target.sessionIndex] = session.copy(
                    title = sessionForm.title,
                    description = sessionForm.description,
                    kind = sessionForm.kind,
                    sport = sessionForm.sport,
                )
            }
            is ExerciseImportStep.Exercise -> {
                val exercises = session.exercises.toMutableList()
                if (target.exerciseIndex !in exercises.indices) return sessions
                exercises[target.exerciseIndex] = exerciseForm.toDraft()
                updated[target.sessionIndex] = session.copy(exercises = exercises)
            }
        }
        return updated
    }

    fun applyAndLoad(list: List<ExerciseSessionDraft>, index: Int) {
        if (list.isEmpty()) {
            onDismiss()
            return
        }
        val bounded = index.coerceIn(0, list.importSteps().lastIndex)
        sessions = list
        stepIndex = bounded
        loadFrom(list, bounded)
    }

    LaunchedEffect(Unit) { loadFrom(sessions, 0) }

    Dialog(onDismissRequest = { showDiscardConfirm = true }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding()
                .heightIn(max = 620.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Review exercises",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stepSubtitle(sourceLabel, step, sessions, stepIndex, steps.size, date),
                    color = SlateGray,
                    fontSize = 13.sp,
                )
                if (replacesDay) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ReplacesDayBanner("Confirming replaces every session on this day, preset items included.")
                }
                Spacer(modifier = Modifier.height(12.dp))

                when (step) {
                    is ExerciseImportStep.Session -> SessionStepBody(
                        form = sessionForm,
                        exerciseNames = currentSession?.exercises.orEmpty().map { it.name },
                        onFormChange = { sessionForm = it },
                        onAddExercise = {
                            val updated = snapshot().toMutableList()
                            val session = updated[step.sessionIndex]
                            updated[step.sessionIndex] = session.copy(
                                exercises = session.exercises + ExerciseImportDraft(),
                            )
                            applyAndLoad(updated, stepIndex + session.exercises.size + 1)
                        },
                        onRemoveSession = {
                            val updated = sessions.toMutableList()
                            updated.removeAt(step.sessionIndex)
                            applyAndLoad(updated, stepIndex)
                        },
                    )
                    is ExerciseImportStep.Exercise -> ExerciseStepBody(
                        form = exerciseForm,
                        kind = currentSession?.kind ?: ExerciseKind.RESISTANCE,
                        sport = currentSession?.sport,
                        muscleVocabulary = muscleVocabulary,
                        onFormChange = { exerciseForm = it },
                        onRemoveExercise = {
                            val updated = sessions.toMutableList()
                            val session = updated[step.sessionIndex]
                            val exercises = session.exercises.toMutableList()
                            exercises.removeAt(step.exerciseIndex)
                            if (exercises.isEmpty()) {
                                updated.removeAt(step.sessionIndex)
                            } else {
                                updated[step.sessionIndex] = session.copy(exercises = exercises)
                            }
                            applyAndLoad(updated, stepIndex - 1)
                        },
                    )
                    null -> Text("Nothing left to review", color = SlateGray, fontSize = 13.sp)
                }

                formError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = DeleteRed, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                val isLast = stepIndex >= steps.lastIndex
                val exerciseCount = sessions.sumOf { it.exercises.size }
                ImportReviewFooter(
                    primaryLabel = when {
                        !isLast -> "Next"
                        replacesDay -> "Replace day ($exerciseCount)"
                        else -> "Add to day ($exerciseCount)"
                    },
                    onPrimary = onPrimary@{
                        val updated = snapshot()
                        val currentError = step?.let { validateStep(it, updated) }
                        if (currentError != null) {
                            sessions = updated
                            formError = currentError
                            return@onPrimary
                        }
                        if (!isLast) {
                            applyAndLoad(updated, stepIndex + 1)
                            return@onPrimary
                        }
                        updated.importSteps().forEachIndexed { index, pending ->
                            val error = validateStep(pending, updated)
                            if (error != null) {
                                applyAndLoad(updated, index)
                                formError = error
                                return@onPrimary
                            }
                        }
                        onConfirmAll(updated.map { it.toWorkoutDaySession() })
                    },
                    onCancel = { showDiscardConfirm = true },
                    onBack = { applyAndLoad(snapshot(), stepIndex - 1) },
                    backEnabled = stepIndex > 0,
                )
            }
        }
    }

    if (showDiscardConfirm) {
        DiscardImportReviewDialog(
            sourceLabel = sourceLabel,
            onKeepReviewing = { showDiscardConfirm = false },
            onDiscard = {
                showDiscardConfirm = false
                onDismiss()
            },
        )
    }
}

private fun stepSubtitle(
    sourceLabel: String,
    step: ExerciseImportStep?,
    sessions: List<ExerciseSessionDraft>,
    stepIndex: Int,
    stepCount: Int,
    date: String,
): String {
    val position = "$sourceLabel · Step ${stepIndex + 1} of $stepCount · $date"
    val session = step?.let { sessions.getOrNull(it.sessionIndex) } ?: return position
    val sessionLabel = "Session ${step.sessionIndex + 1} of ${sessions.size}"
    return when (step) {
        is ExerciseImportStep.Session -> "$position\n$sessionLabel"
        is ExerciseImportStep.Exercise ->
            "$position\n$sessionLabel · Exercise ${step.exerciseIndex + 1} of ${session.exercises.size}"
    }
}

private fun validateStep(step: ExerciseImportStep, sessions: List<ExerciseSessionDraft>): String? {
    val session = sessions.getOrNull(step.sessionIndex) ?: return "Session is missing"
    return when (step) {
        is ExerciseImportStep.Session -> session.validationError()
        is ExerciseImportStep.Exercise -> {
            val exercise = session.exercises.getOrNull(step.exerciseIndex)
                ?: return "Exercise is missing"
            exercise.validationError(session.kind)
        }
    }
}

@Composable
private fun SessionStepBody(
    form: SessionFormState,
    exerciseNames: List<String>,
    onFormChange: (SessionFormState) -> Unit,
    onAddExercise: () -> Unit,
    onRemoveSession: () -> Unit,
) {
    val fieldColors = exerciseImportFieldColors()
    OutlinedTextField(
        value = form.title,
        onValueChange = { onFormChange(form.copy(title = it)) },
        label = { Text("Session title", color = PureBlack) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text("Type", color = PureBlack, fontSize = 13.sp)
    Spacer(modifier = Modifier.height(6.dp))
    ExerciseKindSwitch(
        kind = form.kind,
        onKindChange = { kind ->
            val retitled = if (form.title.isBlank() || form.title == defaultSessionTitle(form.kind)) {
                defaultSessionTitle(kind)
            } else {
                form.title
            }
            onFormChange(form.copy(kind = kind, title = retitled))
        },
    )
    if (form.kind == ExerciseKind.ENDURANCE) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("Sport", color = PureBlack, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OptionalChips(
            options = ExerciseImportDefaults.SPORTS,
            selected = form.sport,
            label = ::sportLabel,
            onSelect = { onFormChange(form.copy(sport = it)) },
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = form.description,
        onValueChange = { onFormChange(form.copy(description = it)) },
        label = { Text("Session notes", color = PureBlack) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = DialogMultilineFieldDefaults.MediumMin,
                max = DialogMultilineFieldDefaults.MediumMax,
            ),
        colors = fieldColors,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = if (exerciseNames.isEmpty()) {
            "No exercises yet"
        } else {
            "${exerciseNames.size} exercises: " + exerciseNames.joinToString(", ") {
                it.ifBlank { "Untitled" }
            }
        },
        color = SlateGray,
        fontSize = 12.sp,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onAddExercise) {
            Icon(Icons.Default.Add, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add exercise", color = NavyDeep)
        }
        TextButton(onClick = onRemoveSession) {
            Text("Remove session", color = DeleteRed)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseStepBody(
    form: ExerciseFormState,
    kind: ExerciseKind,
    sport: String?,
    muscleVocabulary: List<String>,
    onFormChange: (ExerciseFormState) -> Unit,
    onRemoveExercise: () -> Unit,
) {
    val fieldColors = exerciseImportFieldColors()
    OutlinedTextField(
        value = form.name,
        onValueChange = { onFormChange(form.copy(name = it)) },
        label = { Text("Exercise", color = PureBlack) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.description,
        onValueChange = { onFormChange(form.copy(description = it)) },
        label = { Text("Cues and intensity", color = PureBlack) },
        placeholder = {
            Text(
                when (kind) {
                    ExerciseKind.RESISTANCE -> "e.g. Lower to mid-chest, control the eccentric"
                    ExerciseKind.ENDURANCE -> "e.g. Zone 2, 60-70% HRmax"
                    ExerciseKind.OTHER -> "e.g. Focus on footwork; keep heart rate conversational"
                },
                color = SlateGray,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = DialogMultilineFieldDefaults.MediumMin,
                max = DialogMultilineFieldDefaults.MediumMax,
            ),
        colors = fieldColors,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ExerciseNumberField(
            value = form.totalDuration,
            onValueChange = { onFormChange(form.copy(totalDuration = it)) },
            label = "Total duration",
            decimal = true,
            modifier = Modifier.weight(1f),
        )
        UnitChips(
            options = ExerciseImportDefaults.DURATION_UNITS,
            selected = form.totalDurationUnit,
            onSelect = { onFormChange(form.copy(totalDurationUnit = it)) },
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    if (kind == ExerciseKind.ENDURANCE && form.totalDuration.isBlank()) {
        form.toDraft().intervalMinutes()?.let { minutes ->
            Text(
                "Timed intervals add up to ${minutes.toPlainText(1)} min",
                color = SlateGray,
                fontSize = 12.sp,
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (kind == ExerciseKind.RESISTANCE) {
        MuscleTagPicker(
            selected = form.muscles,
            vocabulary = muscleVocabulary,
            onSelectionChange = { onFormChange(form.copy(muscles = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Superset", color = PureBlack, fontSize = 13.sp)
        Text(
            "Exercises sharing a letter alternate round by round on the watch.",
            color = SlateGray,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OptionalChips(
            options = ExerciseImportDefaults.SUPERSET_GROUPS,
            selected = form.supersetGroup,
            label = { it },
            noneLabel = "None",
            onSelect = { onFormChange(form.copy(supersetGroup = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    when (kind) {
        ExerciseKind.RESISTANCE -> {
            OutlinedTextField(
                value = form.timing,
                onValueChange = { onFormChange(form.copy(timing = it)) },
                label = { Text("Tempo", color = PureBlack) },
                placeholder = { Text("ecc-pause-con-pause, e.g. 3-1-1-0", color = SlateGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Load unit", color = SlateGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    UnitChips(
                        options = ExerciseImportDefaults.LOAD_UNITS,
                        selected = form.loadUnit,
                        onSelect = { onFormChange(form.copy(loadUnit = it)) },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rest unit", color = SlateGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    UnitChips(
                        options = ExerciseImportDefaults.REST_UNITS,
                        selected = form.restUnit,
                        onSelect = { onFormChange(form.copy(restUnit = it)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sets (${form.sets.size})", color = PureBlack, fontSize = 13.sp)
                TextButton(
                    onClick = {
                        val template = form.sets.lastOrNull() ?: SetFormState()
                        onFormChange(form.copy(sets = form.sets + template.copy(description = "")))
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add set", color = NavyDeep)
                }
            }
            form.sets.forEachIndexed { index, set ->
                SetEditorCard(
                    setNumber = index + 1,
                    set = set,
                    loadUnit = form.loadUnit,
                    restUnit = form.restUnit,
                    onSetChange = { updated ->
                        onFormChange(
                            form.copy(sets = form.sets.toMutableList().also { it[index] = updated })
                        )
                    },
                    onRemove = {
                        onFormChange(
                            form.copy(sets = form.sets.toMutableList().also { it.removeAt(index) })
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        ExerciseKind.ENDURANCE -> {
            if (sport == "SWIM") {
                Text("Stroke", color = PureBlack, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OptionalChips(
                    options = ExerciseImportDefaults.STROKES,
                    selected = form.stroke,
                    label = ::strokeLabel,
                    onSelect = { onFormChange(form.copy(stroke = it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Intervals (${form.intervals.size})", color = PureBlack, fontSize = 13.sp)
                TextButton(
                    onClick = {
                        val template = form.intervals.lastOrNull()?.copy(description = "")
                            ?: IntervalFormState.defaultFor(sport)
                        onFormChange(form.copy(intervals = form.intervals + template))
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add interval", color = NavyDeep)
                }
            }
            form.intervals.forEachIndexed { index, interval ->
                IntervalEditorCard(
                    intervalNumber = index + 1,
                    interval = interval,
                    showCadence = sport != "SWIM",
                    onIntervalChange = { updated ->
                        onFormChange(
                            form.copy(intervals = form.intervals.toMutableList().also { it[index] = updated })
                        )
                    },
                    onDuplicate = {
                        onFormChange(
                            form.copy(intervals = form.intervals.toMutableList().also { it.add(index + 1, interval) })
                        )
                    },
                    onRemove = {
                        onFormChange(
                            form.copy(intervals = form.intervals.toMutableList().also { it.removeAt(index) })
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        ExerciseKind.OTHER -> {
            Text(
                "Add one exercise per activity. The watch counts down the total duration, then the " +
                    "optional rest before the next one.",
                color = SlateGray,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                ExerciseNumberField(
                    value = form.rest,
                    onValueChange = { onFormChange(form.copy(rest = it)) },
                    label = "Rest after",
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
                UnitChips(
                    options = ExerciseImportDefaults.REST_UNITS,
                    selected = form.restUnit,
                    onSelect = { onFormChange(form.copy(restUnit = it)) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onRemoveExercise) {
        Text("Remove exercise", color = DeleteRed)
    }
}

@Composable
private fun SetEditorCard(
    setNumber: Int,
    set: SetFormState,
    loadUnit: String,
    restUnit: String,
    onSetChange: (SetFormState) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OffWhite,
        border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Set $setNumber", color = PureBlack, fontSize = 13.sp)
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove set $setNumber",
                        tint = DeleteRed,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExerciseNumberField(
                    value = set.load,
                    onValueChange = { onSetChange(set.copy(load = it)) },
                    label = "Load ($loadUnit)",
                    decimal = true,
                    modifier = Modifier.weight(1.2f),
                )
                ExerciseNumberField(
                    value = set.reps,
                    onValueChange = { onSetChange(set.copy(reps = it)) },
                    label = "Reps",
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
                ExerciseNumberField(
                    value = set.rir,
                    onValueChange = { onSetChange(set.copy(rir = it)) },
                    label = "RIR",
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExerciseNumberField(
                    value = set.rest,
                    onValueChange = { onSetChange(set.copy(rest = it)) },
                    label = "Rest ($restUnit)",
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = set.description,
                    onValueChange = { onSetChange(set.copy(description = it)) },
                    label = { Text("Note", color = PureBlack, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    colors = exerciseImportFieldColors(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Drop set", color = PureBlack, fontSize = 13.sp)
                Switch(
                    checked = set.drops.isNotEmpty(),
                    onCheckedChange = { on ->
                        onSetChange(set.copy(drops = if (on) listOf(set.suggestedDrop()) else emptyList()))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = NavyDeep,
                        uncheckedThumbColor = SlateGray,
                        uncheckedTrackColor = PureWhite,
                        uncheckedBorderColor = PureBlack.copy(alpha = 0.35f),
                    ),
                )
            }
            set.drops.forEachIndexed { dropIndex, drop ->
                HorizontalDivider(color = DropSetOrange.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Drop ${dropIndex + 1}", color = DropSetOrange, fontSize = 12.sp)
                    IconButton(
                        onClick = {
                            onSetChange(set.copy(drops = set.drops.toMutableList().also { it.removeAt(dropIndex) }))
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove drop ${dropIndex + 1}",
                            tint = DeleteRed,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fun update(transform: (DropFormState) -> DropFormState) {
                        onSetChange(
                            set.copy(drops = set.drops.toMutableList().also { it[dropIndex] = transform(drop) })
                        )
                    }
                    ExerciseNumberField(
                        value = drop.load,
                        onValueChange = { value -> update { it.copy(load = value) } },
                        label = "Load ($loadUnit)",
                        decimal = true,
                        modifier = Modifier.weight(1.2f),
                    )
                    ExerciseNumberField(
                        value = drop.reps,
                        onValueChange = { value -> update { it.copy(reps = value) } },
                        label = "Reps",
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                    ExerciseNumberField(
                        value = drop.rir,
                        onValueChange = { value -> update { it.copy(rir = value) } },
                        label = "RIR",
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (set.drops.isNotEmpty()) {
                TextButton(onClick = { onSetChange(set.copy(drops = set.drops + set.suggestedDrop())) }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DropSetOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add drop", color = DropSetOrange, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun IntervalEditorCard(
    intervalNumber: Int,
    interval: IntervalFormState,
    showCadence: Boolean,
    onIntervalChange: (IntervalFormState) -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OffWhite,
        border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Interval $intervalNumber", color = PureBlack, fontSize = 13.sp)
                Row {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Duplicate interval $intervalNumber",
                            tint = NavyDeep,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove interval $intervalNumber",
                            tint = DeleteRed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            UnitChips(
                options = listOf(INTERVAL_BY_TIME, INTERVAL_BY_DISTANCE),
                selected = if (interval.byDistance) INTERVAL_BY_DISTANCE else INTERVAL_BY_TIME,
                onSelect = { onIntervalChange(interval.copy(byDistance = it == INTERVAL_BY_DISTANCE)) },
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (interval.byDistance) {
                    ExerciseNumberField(
                        value = interval.distance,
                        onValueChange = { onIntervalChange(interval.copy(distance = it)) },
                        label = "Distance",
                        decimal = true,
                        modifier = Modifier.weight(1f),
                    )
                    UnitChips(
                        options = ExerciseImportDefaults.DISTANCE_UNITS,
                        selected = interval.distanceUnit,
                        onSelect = { onIntervalChange(interval.copy(distanceUnit = it)) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    ExerciseNumberField(
                        value = interval.duration,
                        onValueChange = { onIntervalChange(interval.copy(duration = it)) },
                        label = "Duration",
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                    UnitChips(
                        options = ExerciseImportDefaults.DURATION_UNITS,
                        selected = interval.durationUnit,
                        onSelect = { onIntervalChange(interval.copy(durationUnit = it)) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Zone", color = SlateGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OptionalChips(
                options = (1..ExerciseImportDefaults.MAX_ZONE).map { it.toString() },
                selected = interval.zone,
                label = { "Z$it" },
                onSelect = { onIntervalChange(interval.copy(zone = it)) },
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExerciseNumberField(
                    value = interval.rpe,
                    onValueChange = { onIntervalChange(interval.copy(rpe = it)) },
                    label = "RPE",
                    decimal = true,
                    modifier = Modifier.weight(1f),
                )
                if (showCadence) {
                    ExerciseNumberField(
                        value = interval.cadence,
                        onValueChange = { onIntervalChange(interval.copy(cadence = it)) },
                        label = "Cadence (${interval.cadenceUnit})",
                        decimal = false,
                        modifier = Modifier.weight(1.3f),
                    )
                }
                ExerciseNumberField(
                    value = interval.rest,
                    onValueChange = { onIntervalChange(interval.copy(rest = it)) },
                    label = "Rest (${interval.restUnit})",
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UnitChips(
                    options = ExerciseImportDefaults.REST_UNITS,
                    selected = interval.restUnit,
                    onSelect = { onIntervalChange(interval.copy(restUnit = it)) },
                )
                OutlinedTextField(
                    value = interval.description,
                    onValueChange = { onIntervalChange(interval.copy(description = it)) },
                    label = { Text("Note", color = PureBlack, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = exerciseImportFieldColors(),
                )
            }
        }
    }
}

private const val INTERVAL_BY_TIME = "Time"
private const val INTERVAL_BY_DISTANCE = "Distance"

/** Chips where tapping the selected option clears it; [noneLabel] adds an explicit "none" chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionalChips(
    options: List<String>,
    selected: String?,
    label: (String) -> String,
    onSelect: (String?) -> Unit,
    noneLabel: String? = null,
) {
    @Composable
    fun Chip(text: String, isSelected: Boolean, onClick: () -> Unit) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) NavyDeep else PureWhite,
            border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.25f)),
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Text(
                text = text,
                color = if (isSelected) PureWhite else PureBlack,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        noneLabel?.let { Chip(it, selected == null) { onSelect(null) } }
        options.forEach { option ->
            val isSelected = option.equals(selected, ignoreCase = true)
            Chip(label(option), isSelected) { onSelect(if (isSelected) null else option) }
        }
    }
}

private fun sportLabel(sport: String): String = when (sport) {
    "RUN" -> "Run"
    "BIKE" -> "Bike"
    "SWIM" -> "Swim"
    else -> sport
}

private fun strokeLabel(stroke: String): String = when (stroke) {
    "IM" -> "IM"
    else -> stroke.lowercase().replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleTagPicker(
    selected: Map<String, Double>,
    vocabulary: List<String>,
    onSelectionChange: (Map<String, Double>) -> Unit,
) {
    var showCustomTag by remember { mutableStateOf(false) }
    var customTag by remember { mutableStateOf("") }
    val candidates = remember(vocabulary, selected) {
        (vocabulary + selected.keys).mapNotNull { normalizeMuscleTag(it) }.distinct()
    }
    val primary = selected.filterValues { it >= MuscleTargets.PRIMARY }.keys
    val secondary = selected.filterValues { it < MuscleTargets.PRIMARY }.keys

    fun cycle(tag: String) {
        val next = LinkedHashMap(selected)
        when (val weight = selected[tag]) {
            null -> next[tag] = MuscleTargets.PRIMARY
            else -> if (weight >= MuscleTargets.PRIMARY) next[tag] = MuscleTargets.SECONDARY else next.remove(tag)
        }
        onSelectionChange(next)
    }

    Text("Muscles", color = PureBlack, fontSize = 13.sp)
    Text(
        text = if (selected.isEmpty()) {
            "Tap once for primary (1 set each), twice for secondary (½ set), again to remove."
        } else {
            listOfNotNull(
                primary.takeIf { it.isNotEmpty() }?.let { "Primary: ${it.joinToString(", ")}" },
                secondary.takeIf { it.isNotEmpty() }?.let { "Secondary: ${it.joinToString(", ")}" },
            ).joinToString(" · ")
        },
        color = SlateGray,
        fontSize = 12.sp,
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        candidates.forEach { tag ->
            val weight = selected[tag]
            val isPrimary = weight != null && weight >= MuscleTargets.PRIMARY
            val isSecondary = weight != null && !isPrimary
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isPrimary -> NavyDeep
                    isSecondary -> NavyDeep.copy(alpha = 0.12f)
                    else -> PureWhite
                },
                border = BorderStroke(
                    1.dp,
                    if (isSecondary) NavyDeep else PureBlack.copy(alpha = 0.25f),
                ),
                modifier = Modifier.clickable { cycle(tag) },
            ) {
                Text(
                    text = if (isSecondary) "$tag ×${weight.toPlainText(2)}" else tag,
                    color = if (isPrimary) PureWhite else PureBlack,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, NavyDeep.copy(alpha = 0.5f)),
            modifier = Modifier.clickable {
                customTag = ""
                showCustomTag = true
            },
        ) {
            Text(
                text = "+ tag",
                color = NavyDeep,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    if (showCustomTag) {
        AlertDialog(
            onDismissRequest = { showCustomTag = false },
            containerColor = PureWhite,
            title = { Text("New muscle tag", color = PureBlack) },
            text = {
                OutlinedTextField(
                    value = customTag,
                    onValueChange = { customTag = it },
                    singleLine = true,
                    label = { Text("Tag", color = PureBlack) },
                    colors = exerciseImportFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    normalizeMuscleTag(customTag)?.let { tag ->
                        if (tag !in selected) {
                            val weight = if (primary.isEmpty()) MuscleTargets.PRIMARY else MuscleTargets.SECONDARY
                            onSelectionChange(LinkedHashMap(selected).apply { put(tag, weight) })
                        }
                    }
                    showCustomTag = false
                }) {
                    Text("Add", color = NavyDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTag = false }) {
                    Text("Cancel", color = PureBlack)
                }
            },
        )
    }
}

@Composable
private fun ExerciseKindSwitch(
    kind: ExerciseKind,
    onKindChange: (ExerciseKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PureBlack.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        ExerciseKind.entries.forEach { option ->
            val selected = kind == option
            Surface(
                onClick = { onKindChange(option) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) NavyDeep else PureWhite,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = option.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (selected) PureWhite else PureBlack,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun UnitChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = option.equals(selected, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) NavyDeep else PureWhite,
                border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.25f)),
                modifier = Modifier.clickable { onSelect(option) },
            ) {
                Text(
                    text = option,
                    color = if (isSelected) PureWhite else PureBlack,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ExerciseNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    decimal: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(filterNumericInput(raw, decimal)) },
        label = { Text(label, color = PureBlack, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
        colors = exerciseImportFieldColors(),
    )
}

private fun filterNumericInput(raw: String, decimal: Boolean): String {
    val builder = StringBuilder()
    var dotSeen = false
    for (char in raw) {
        when {
            char.isDigit() -> builder.append(char)
            decimal && (char == '.' || char == ',') && !dotSeen -> {
                dotSeen = true
                builder.append('.')
            }
        }
    }
    return builder.toString().take(8)
}

@Composable
private fun exerciseImportFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PureBlack,
    unfocusedTextColor = PureBlack,
    cursorColor = NavyDeep,
    focusedBorderColor = NavyDeep,
    unfocusedBorderColor = PureBlack.copy(alpha = 0.5f),
    focusedLabelColor = PureBlack,
    unfocusedLabelColor = SlateGray,
    focusedPlaceholderColor = SlateGray,
    unfocusedPlaceholderColor = SlateGray,
    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite,
)

/* --- Form state: text-backed so partially typed numbers survive recomposition --- */

private data class SessionFormState(
    val title: String = "",
    val description: String = "",
    val kind: ExerciseKind = ExerciseKind.RESISTANCE,
    val sport: String? = null,
)

private data class ExerciseFormState(
    val name: String = "",
    val description: String = "",
    val muscles: Map<String, Double> = emptyMap(),
    val supersetGroup: String? = null,
    val timing: String = "",
    val loadUnit: String = ExerciseImportDefaults.LOAD_UNIT,
    val restUnit: String = ExerciseImportDefaults.REST_UNIT,
    val totalDuration: String = "",
    val totalDurationUnit: String = ExerciseImportDefaults.EXERCISE_DURATION_UNIT,
    val stroke: String? = null,
    /** Exercise-level rest after (OTHER sequenced activities). */
    val rest: String = "",
    val sets: List<SetFormState> = emptyList(),
    val intervals: List<IntervalFormState> = emptyList(),
)

private data class SetFormState(
    val description: String = "",
    val load: String = "",
    val reps: String = "",
    val rir: String = "",
    val rest: String = "",
    /** Timed sets (planks, carries) round-trip untouched; the calendar edits them after import. */
    val duration: String = "",
    val durationUnit: String = ExerciseImportDefaults.SET_DURATION_UNIT,
    val drops: List<DropFormState> = emptyList(),
)

private data class DropFormState(
    val description: String = "",
    val load: String = "",
    val reps: String = "",
    val rir: String = "",
)

private data class IntervalFormState(
    val description: String = "",
    val byDistance: Boolean = false,
    val duration: String = "",
    val durationUnit: String = ExerciseImportDefaults.INTERVAL_DURATION_UNIT,
    val distance: String = "",
    val distanceUnit: String = ExerciseImportDefaults.DISTANCE_UNIT,
    val zone: String? = null,
    val rpe: String = "",
    val cadence: String = "",
    val cadenceUnit: String = ExerciseImportDefaults.CADENCE_UNIT,
    val rest: String = "",
    val restUnit: String = ExerciseImportDefaults.REST_UNIT,
) {
    companion object {
        fun defaultFor(sport: String?) = when (sport) {
            "SWIM" -> IntervalFormState(byDistance = true, distanceUnit = "yd")
            else -> IntervalFormState()
        }
    }
}

/** Next drop starts ~25% lighter than the previous step, at the same reps. */
private fun SetFormState.suggestedDrop(): DropFormState {
    val previous = drops.lastOrNull()
    val previousLoad = (previous?.load ?: load).toDoubleOrNullSafe()
    val suggested = previousLoad?.let { (it * 0.75 / 2.5).roundToInt() * 2.5 }?.takeIf { it > 0.0 }
    return DropFormState(
        load = suggested.toPlainText(),
        reps = previous?.reps ?: reps,
    )
}

private fun ExerciseSessionDraft.toForm() = SessionFormState(
    title = title,
    description = description,
    kind = kind,
    sport = sport,
)

private fun ExerciseImportDraft.toForm(defaultLoadUnit: String) = ExerciseFormState(
    name = name,
    description = description,
    muscles = targets,
    supersetGroup = supersetGroup,
    timing = timing.orEmpty(),
    loadUnit = sets.firstOrNull()?.unitLoad ?: defaultLoadUnit,
    restUnit = sets.firstOrNull()?.restUnit ?: unitOrDefault(restUnit, ExerciseImportDefaults.REST_UNIT),
    totalDuration = totalDuration.toPlainText(),
    totalDurationUnit = unitOrDefault(unitDuration, ExerciseImportDefaults.EXERCISE_DURATION_UNIT),
    stroke = stroke,
    rest = rest?.toString().orEmpty(),
    sets = sets.map { it.toForm() },
    intervals = intervals.map { it.toForm() },
)

private fun ExerciseSetDraft.toForm() = SetFormState(
    description = description,
    load = targetLoad.toPlainText(),
    reps = targetReps?.toString().orEmpty(),
    rir = targetRir?.toString().orEmpty(),
    rest = rest?.toString().orEmpty(),
    duration = targetDuration?.toString().orEmpty(),
    durationUnit = unitDuration,
    drops = drops.map { drop ->
        DropFormState(
            description = drop.description,
            load = drop.targetLoad.toPlainText(),
            reps = drop.targetReps?.toString().orEmpty(),
            rir = drop.targetRir?.toString().orEmpty(),
        )
    },
)

private fun ExerciseIntervalDraft.toForm() = IntervalFormState(
    description = description,
    byDistance = targetDistance != null && targetDuration == null,
    duration = targetDuration?.toString().orEmpty(),
    durationUnit = unitOrDefault(unitDuration, ExerciseImportDefaults.INTERVAL_DURATION_UNIT),
    distance = targetDistance.toPlainText(),
    distanceUnit = unitOrDefault(unitDistance, ExerciseImportDefaults.DISTANCE_UNIT),
    zone = targetZone?.toString(),
    rpe = targetRpe.toPlainText(1),
    cadence = targetCadence?.toString().orEmpty(),
    cadenceUnit = unitOrDefault(unitCadence, ExerciseImportDefaults.CADENCE_UNIT),
    rest = rest?.toString().orEmpty(),
    restUnit = unitOrDefault(restUnit, ExerciseImportDefaults.REST_UNIT),
)

private fun ExerciseFormState.toDraft() = ExerciseImportDraft(
    name = name.trim(),
    description = description.trim(),
    targets = muscles,
    supersetGroup = supersetGroup,
    timing = timing.trim().ifBlank { null },
    totalDuration = totalDuration.toDoubleOrNullSafe(),
    unitDuration = totalDurationUnit,
    stroke = stroke,
    rest = rest.toIntOrNullSafe(),
    restUnit = restUnit,
    sets = sets.map { it.toDraft(loadUnit, restUnit) },
    intervals = intervals.map { it.toDraft() },
)

private fun SetFormState.toDraft(loadUnit: String, restUnit: String) = ExerciseSetDraft(
    description = description.trim(),
    targetLoad = load.toDoubleOrNullSafe(),
    unitLoad = loadUnit,
    targetReps = reps.toIntOrNullSafe(),
    targetRir = rir.toIntOrNullSafe(),
    rest = rest.toIntOrNullSafe(),
    restUnit = restUnit,
    targetDuration = duration.toIntOrNullSafe(),
    unitDuration = durationUnit,
    drops = drops.map { drop ->
        ExerciseDropDraft(
            description = drop.description.trim(),
            targetLoad = drop.load.toDoubleOrNullSafe(),
            targetReps = drop.reps.toIntOrNullSafe(),
            targetRir = drop.rir.toIntOrNullSafe(),
        )
    },
)

private fun IntervalFormState.toDraft() = ExerciseIntervalDraft(
    description = description.trim(),
    targetDuration = if (byDistance) null else duration.toIntOrNullSafe(),
    unitDuration = durationUnit,
    targetDistance = if (byDistance) distance.toDoubleOrNullSafe() else null,
    unitDistance = distanceUnit,
    targetZone = zone?.toIntOrNull(),
    targetRpe = normalizeRpe(rpe.toDoubleOrNullSafe()),
    targetCadence = cadence.toIntOrNullSafe(),
    unitCadence = cadenceUnit,
    rest = rest.toIntOrNullSafe(),
    restUnit = restUnit,
)

private fun unitOrDefault(value: String, fallback: String): String =
    value.trim().ifBlank { fallback }

private fun String.toIntOrNullSafe(): Int? = trim().toIntOrNull()

private fun String.toDoubleOrNullSafe(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun Double?.toPlainText(maxDecimals: Int = 3): String = when {
    this == null -> ""
    this == kotlin.math.floor(this) && kotlin.math.abs(this) < 1e9 -> toLong().toString()
    else -> java.math.BigDecimal(this)
        .setScale(maxDecimals, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
