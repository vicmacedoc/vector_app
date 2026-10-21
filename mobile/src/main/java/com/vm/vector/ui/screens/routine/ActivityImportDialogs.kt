package com.vm.vector.ui.screens.routine

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityLabel
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.ActivityImportDefaults
import com.vm.vector.data.ActivityImportDraft
import com.vm.vector.data.ActivityImportSource
import com.vm.vector.data.CalendarAiMode
import com.vm.vector.data.CalendarDayContext
import com.vm.vector.data.GeminiActivityClient
import com.vm.vector.data.GoogleCalendarClient
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.mergeActivityImportDraft
import com.vm.vector.data.resolveImportLabelId
import com.vm.vector.data.toActivityEntry
import com.vm.vector.data.validationError
import com.vm.vector.ui.components.DialogMultilineFieldDefaults
import com.vm.vector.ui.components.DiscardImportReviewDialog
import com.vm.vector.ui.components.ImportReviewFooter
import com.vm.vector.ui.components.KeyboardAwareDialogColumn
import com.vm.vector.ui.screens.calendar.ReplacesDayBanner
import com.vm.vector.ui.screens.calendar.calendarAiDialogTitle
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun FillActivityFromAiDialog(
    date: String,
    labelNames: List<String>,
    mode: CalendarAiMode,
    onDismiss: () -> Unit,
    onDraftsReady: (List<ActivityImportDraft>) -> Unit,
    dayContextProvider: suspend () -> CalendarDayContext = { CalendarDayContext() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { GeminiActivityClient(context.applicationContext) }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val fieldColors = activityImportFieldColors()
    val descriptionOptional = mode == CalendarAiMode.PLAN_PRESET

    fun send() {
        if (isLoading) return
        if (description.isBlank() && !descriptionOptional) {
            errorMessage = "Describe the activities for this day"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val dayContext = runCatching { dayContextProvider() }.getOrDefault(CalendarDayContext())
            val result = client.estimateActivities(
                mode = mode,
                description = description,
                date = date,
                existingLabels = labelNames,
                dayContext = dayContext,
            )
            isLoading = false
            result.onSuccess { drafts ->
                onDraftsReady(drafts)
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
                            "Describe one activity for $date: title, time, label and notes."
                        CalendarAiMode.MULTIPLE ->
                            "Describe the activities to add on $date. Titles, times, labels and notes help."
                        CalendarAiMode.PLAN_SCRATCH ->
                            "Describe the whole day for $date. The result replaces every activity on the day."
                        CalendarAiMode.PLAN_PRESET ->
                            "Starts from this weekday's preset. Describe what to change, or leave it blank to " +
                                "keep the preset. The result replaces every activity on the day."
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
                            "e.g. Gym 7–8:30am (Health), deep work 9–12 planned, lunch walk 30min after noon…",
                            color = SlateGray,
                        )
                    },
                    enabled = !isLoading,
                    colors = fieldColors,
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp)
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
                        Text("Reading activities…", color = SlateGray, fontSize = 13.sp)
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
 * Fetches Google Calendar events for [date] (Routine Calendar selected day),
 * then hands drafts to [onDraftsReady]. Handles sign-in / Calendar scope consent.
 */
@Composable
fun ImportActivitiesFromGoogleCalendarGate(
    date: String,
    onDismiss: () -> Unit,
    onDraftsReady: (List<ActivityImportDraft>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { GoogleDriveAuthManager(context) }
    val calendarClient = remember { GoogleCalendarClient(context, authManager) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingRecoveryIntent by remember { mutableStateOf<android.content.Intent?>(null) }

    fun fetch() {
        isLoading = true
        errorMessage = null
        scope.launch {
            when (val result = calendarClient.fetchActivitiesForDate(date)) {
                is GoogleCalendarClient.FetchResult.Success -> {
                    isLoading = false
                    if (result.drafts.isEmpty()) {
                        errorMessage = "No events on $date"
                    } else {
                        onDraftsReady(result.drafts)
                    }
                }
                GoogleCalendarClient.FetchResult.Unauthorized,
                GoogleCalendarClient.FetchResult.NeedsCalendarPermission,
                -> {
                    isLoading = false
                    errorMessage = "Google sign-in with Calendar access required"
                }
                is GoogleCalendarClient.FetchResult.NeedsUserRecovery -> {
                    isLoading = false
                    pendingRecoveryIntent = result.intent
                }
                is GoogleCalendarClient.FetchResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager.handleSignInResult(result.data) { account ->
            if (account == null) {
                errorMessage = "Google sign-in cancelled"
                isLoading = false
            } else {
                fetch()
            }
        }
    }

    val recoveryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingRecoveryIntent = null
        if (result.resultCode == Activity.RESULT_OK) {
            fetch()
        } else {
            errorMessage = "Calendar permission denied"
            isLoading = false
        }
    }

    LaunchedEffect(date) {
        val account = authManager.getLastSignedInAccount()
        when {
            account == null || !authManager.hasCalendarPermission(account) -> {
                isLoading = false
                errorMessage = "Google sign-in with Calendar access required"
            }
            else -> fetch()
        }
    }

    LaunchedEffect(pendingRecoveryIntent) {
        pendingRecoveryIntent?.let { recoveryLauncher.launch(it) }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = PureWhite,
        title = { Text("Import from Google Calendar", color = PureBlack) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Events for $date from your primary calendar.",
                    color = SlateGray,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = NavyDeep,
                                strokeWidth = 2.dp,
                            )
                            Text("Loading events…", color = SlateGray, fontSize = 13.sp)
                        }
                    }
                    errorMessage != null -> {
                        Text(errorMessage!!, color = Color(0xFFD32F2F), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            val needsAuth = errorMessage?.contains("sign-in", ignoreCase = true) == true
            if (needsAuth) {
                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        signInLauncher.launch(authManager.getSignInIntent())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyDeep,
                        contentColor = PureWhite,
                    ),
                ) { Text("Sign in") }
            } else if (!isLoading && errorMessage != null) {
                Button(
                    onClick = { fetch() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyDeep,
                        contentColor = PureWhite,
                    ),
                ) { Text("Retry") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = PureBlack)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActivityImportWizardDialog(
    initialDrafts: List<ActivityImportDraft>,
    labels: List<ActivityLabel>,
    date: String,
    onDismiss: () -> Unit,
    onConfirmAll: (List<ActivityEntry>) -> Unit,
    allowExclude: Boolean = false,
    /** Plan modes: confirming replaces every activity on the day. */
    replacesDay: Boolean = false,
) {
    val defaultLabelId = labels.firstOrNull()?.id.orEmpty()
    var drafts by remember {
        mutableStateOf(
            initialDrafts.map { draft ->
                mergeActivityImportDraft(draft, labels)
            }
        )
    }
    var index by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf(ActivityImportDefaults.START_TIME) }
    var endTime by remember { mutableStateOf(ActivityImportDefaults.END_TIME) }
    var labelId by remember { mutableStateOf(defaultLabelId) }
    var type by remember { mutableStateOf(ActivityImportDefaults.TYPE) }
    var description by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf<ActivityTimePickerKind?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val fieldColors = activityImportFieldColors()
    val sourceLabel = when (drafts.getOrNull(index)?.source) {
        ActivityImportSource.GOOGLE_CALENDAR -> "Google Calendar"
        ActivityImportSource.AI -> "AI"
        ActivityImportSource.CLIPBOARD -> "Clipboard"
        ActivityImportSource.LIBRARY -> "Library"
        null -> "Import"
    }

    fun loadIndex(i: Int) {
        val draft = drafts.getOrNull(i) ?: return
        formError = null
        title = draft.title
        startTime = draft.startTime
        endTime = draft.endTime
        labelId = resolveImportLabelId(draft, labels, defaultLabelId)
        type = draft.type
        description = draft.description
    }

    fun snapshotCurrent(): ActivityImportDraft {
        val current = drafts.getOrNull(index)
        return ActivityImportDraft(
            title = title,
            startTime = startTime,
            endTime = endTime,
            labelName = labels.find { it.id == labelId }?.name,
            type = type,
            description = description,
            source = current?.source ?: ActivityImportSource.AI,
            externalId = current?.externalId,
        )
    }

    fun persistCurrentIntoList(): List<ActivityImportDraft> {
        val updated = drafts.toMutableList()
        if (index in updated.indices) {
            updated[index] = snapshotCurrent()
        }
        return updated
    }

    LaunchedEffect(initialDrafts, labels) {
        loadIndex(0)
    }

    // Reload form when labels list identity changes after create
    LaunchedEffect(labels) {
        if (labelId.isBlank() || labels.none { it.id == labelId }) {
            labelId = labels.firstOrNull()?.id.orEmpty()
        }
    }

    Dialog(onDismissRequest = { showDiscardConfirm = true }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding()
                .heightIn(max = 600.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Review activities",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$sourceLabel · ${index + 1} of ${drafts.size} · $date",
                    color = SlateGray,
                    fontSize = 13.sp,
                )
                if (replacesDay) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ReplacesDayBanner("Confirming replaces every activity on this day, preset items included.")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = PureBlack) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        ActivityTimeInputChip(
                            timeText = startTime,
                            onClick = { showTimePicker = ActivityTimePickerKind.Start },
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("End", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        ActivityTimeInputChip(
                            timeText = endTime,
                            onClick = { showTimePicker = ActivityTimePickerKind.End },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Label", color = PureBlack, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    labels.forEach { label ->
                        val selected = label.id == labelId
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Color(label.colorArgb) else OffWhite,
                            border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { labelId = label.id },
                        ) {
                            Text(
                                text = label.name,
                                color = if (selected) PureWhite else PureBlack,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Type", color = PureBlack, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                ActivityTypeSwitch(type = type, onTypeChange = { type = it })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = PureBlack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = DialogMultilineFieldDefaults.MediumMin,
                            max = DialogMultilineFieldDefaults.MediumMax,
                        ),
                    colors = fieldColors,
                )
                formError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                val isLast = index >= drafts.lastIndex
                ImportReviewFooter(
                    primaryLabel = when {
                        isLast && replacesDay -> "Replace day (${drafts.size})"
                        isLast && drafts.size == 1 -> "Add"
                        isLast -> "Add all (${drafts.size})"
                        else -> "Next"
                    },
                    onPrimary = onPrimary@{
                        val snap = snapshotCurrent()
                        val err = snap.validationError(labels, defaultLabelId)
                        if (err != null) {
                            formError = err
                            return@onPrimary
                        }
                        val updated = persistCurrentIntoList().toMutableList()
                        updated[index] = snap
                        drafts = updated
                        if (!isLast) {
                            index += 1
                            loadIndex(index)
                            return@onPrimary
                        }
                        for ((i, d) in updated.withIndex()) {
                            val e = d.validationError(labels, defaultLabelId)
                            if (e != null) {
                                index = i
                                loadIndex(i)
                                formError = "Activity ${i + 1}: $e"
                                return@onPrimary
                            }
                        }
                        val entries = updated.mapNotNull { d ->
                            d.toActivityEntry(date, labels, defaultLabelId)
                        }
                        if (entries.size != updated.size) {
                            formError = "Some activities are incomplete"
                            return@onPrimary
                        }
                        onConfirmAll(entries)
                    },
                    onCancel = { showDiscardConfirm = true },
                    onBack = {
                        drafts = persistCurrentIntoList()
                        if (index > 0) {
                            index -= 1
                            loadIndex(index)
                        }
                    },
                    backEnabled = index > 0,
                    extraActions = {
                        if (allowExclude && drafts.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    val updated = persistCurrentIntoList().toMutableList()
                                    if (index in updated.indices) updated.removeAt(index)
                                    if (updated.isEmpty()) {
                                        formError = "Keep at least one activity, or cancel"
                                        return@TextButton
                                    }
                                    drafts = updated
                                    index = index.coerceAtMost(updated.lastIndex)
                                    loadIndex(index)
                                },
                            ) { Text("Exclude", color = DeleteRed) }
                        }
                    },
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

    showTimePicker?.let { kind ->
        val initial = when (kind) {
            ActivityTimePickerKind.Start -> parseActivityHhMmToMinutes(startTime) ?: (9 * 60)
            ActivityTimePickerKind.End -> parseActivityHhMmToMinutes(endTime) ?: (10 * 60)
        }
        ActivityTimePickerDialog(
            initialMinutes = initial,
            onDismiss = { showTimePicker = null },
            onConfirm = { minutes ->
                val formatted = minutesToActivityHhMm(minutes)
                when (kind) {
                    ActivityTimePickerKind.Start -> startTime = formatted
                    ActivityTimePickerKind.End -> endTime = formatted
                }
                showTimePicker = null
            },
        )
    }
}

@Composable
private fun activityImportFieldColors() = OutlinedTextFieldDefaults.colors(
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
