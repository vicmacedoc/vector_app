package com.vm.vector.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.vm.vector.R
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import com.vm.core.models.ActivityLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import com.vm.vector.data.PresetCategory
import com.vm.vector.ui.viewmodel.SleepPickerKind
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import com.vm.vector.data.GeminiApiKeySource
import com.vm.vector.data.LearningSettings
import com.vm.vector.data.MotionSettings
import java.time.DayOfWeek
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.vm.vector.ui.viewmodel.SettingsViewModel
import com.vm.vector.ui.viewmodel.SettingsViewModelFactory
import com.vm.vector.ui.screens.routine.CreateActivityLabelDialog
import com.vm.vector.data.backup.BackupSnapshotInfo
import com.vm.vector.ui.backup.RestorePickerDialog
import com.vm.vector.ui.backup.formatBackupTime

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingPresetCategory by remember { mutableStateOf<PresetCategory?>(null) }
    var geminiKeyVisible by remember { mutableStateOf(false) }
    var showCreateActivityLabel by remember { mutableStateOf(false) }
    var newActivityLabelName by remember { mutableStateOf("") }
    var newActivityLabelColor by remember { mutableStateOf(0xFF1565C0.toInt()) }
    var pendingDeleteActivityLabel by remember { mutableStateOf<ActivityLabel?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupSnapshotInfo?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val category = pendingPresetCategory
        pendingPresetCategory = null
        if (uri != null && category != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else "preset.json"
                    } else "preset.json"
                } ?: "preset.json"
                viewModel.onPresetFilePicked(category, content, displayName)
            } catch (e: Exception) {
                viewModel.onPresetFilePicked(category, "", "preset.json") // will show validation error
            }
        }
    }

    fun launchPresetPicker(category: PresetCategory) {
        pendingPresetCategory = category
        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
    }

    LaunchedEffect(uiState.showSnackbar) {
        if (uiState.showSnackbar) {
            snackbarHostState.showSnackbar(uiState.snackbarMessage)
            viewModel.dismissSnackbar()
        }
    }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureWhite)
                    .padding(
                        start = paddingValues.calculateLeftPadding(layoutDirection),
                        end = paddingValues.calculateRightPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ——— About ———
                    AboutVectorCard()

                    // ——— Sleep Settings ———
                    SettingsSectionTitle("Sleep Settings")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OffWhite),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Wake-up", style = MaterialTheme.typography.bodyMedium, color = SlateGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TimeChip(
                                        minutes = uiState.sleepWakeupMinutes,
                                        onClick = { viewModel.showSleepTimePicker(SleepPickerKind.Wakeup) }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Bedtime", style = MaterialTheme.typography.bodyMedium, color = SlateGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TimeChip(
                                        minutes = uiState.sleepBedtimeMinutes,
                                        onClick = { viewModel.showSleepTimePicker(SleepPickerKind.Bedtime) }
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.setPreferences() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                            ) {
                                Text("Set Preferences", color = PureWhite)
                            }
                            val base = uiState.wakeAlarmBaseMinutes
                            val offsets = uiState.wakeAlarmOffsets
                            val hasAlarms = base != null && offsets.isNotEmpty()
                            if (hasAlarms) {
                                var alarmsExpanded by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { alarmsExpanded = !alarmsExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Alarms",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PureBlack,
                                    )
                                    Icon(
                                        imageVector = if (alarmsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (alarmsExpanded) "Collapse" else "Expand",
                                        tint = SlateGray,
                                    )
                                }
                                if (alarmsExpanded) {
                                    offsets.sorted().forEach { offset ->
                                        val totalMin = (base + offset) % (24 * 60)
                                        val label = if (offset == 0) "Wake-up" else "+${offset} min"
                                        AlarmReminderTile(
                                            label = label,
                                            timeText = "%02d:%02d".format(totalMin / 60, totalMin % 60),
                                            onRemove = { viewModel.removeWakeAlarm(offset) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ——— Notification Settings ———
                    SettingsSectionTitle("Notification Settings")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OffWhite),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { viewModel.showFilloutTimePicker(true) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                            ) {
                                Text("Fill-out Reminder", color = PureWhite)
                            }
                            uiState.filloutReminderMinutes?.let { minutes ->
                                val totalMin = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
                                AlarmReminderTile(
                                    label = "Fill-out reminder",
                                    timeText = "%02d:%02d".format(totalMin / 60, totalMin % 60),
                                    onRemove = { viewModel.removeFilloutReminder() }
                                )
                            }
                        }
                    }

                    // ——— Preset Settings ———
                    SettingsSectionTitle("Preset Settings")
                    PresetTile(
                        label = "Workout",
                        activeFileName = uiState.workoutPresetName,
                        presetJsonName = uiState.workoutPresetJsonName,
                        onBrowse = { launchPresetPicker(PresetCategory.Workout) },
                        onRemove = { viewModel.removePreset(PresetCategory.Workout) },
                    )
                    PresetTile(
                        label = "Food",
                        activeFileName = uiState.foodPresetName,
                        presetJsonName = uiState.foodPresetJsonName,
                        onBrowse = { launchPresetPicker(PresetCategory.Food) },
                        onRemove = { viewModel.removePreset(PresetCategory.Food) },
                    )
                    PresetTile(
                        label = "Routine",
                        activeFileName = uiState.routinePresetName,
                        presetJsonName = uiState.routinePresetJsonName,
                        onBrowse = { launchPresetPicker(PresetCategory.Routine) },
                        onRemove = { viewModel.removePreset(PresetCategory.Routine) },
                    )
                    RoutineLabelsSettingsSection(
                        labels = uiState.activityLabels,
                        onAddClick = {
                            newActivityLabelName = ""
                            newActivityLabelColor = 0xFF1565C0.toInt()
                            showCreateActivityLabel = true
                        },
                        onDeleteLabel = { label -> pendingDeleteActivityLabel = label },
                    )

                    // ——— AI Settings ———
                    SettingsSectionTitle("AI Settings")
                    Text(
                        text = uiState.geminiKeyStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (uiState.geminiKeySource) {
                            GeminiApiKeySource.None -> SlateGray
                            else -> PureBlack
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.geminiApiKey,
                        onValueChange = viewModel::updateGeminiApiKey,
                        label = { Text("Gemini API key", color = PureBlack) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (geminiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { geminiKeyVisible = !geminiKeyVisible }) {
                                Icon(
                                    imageVector = if (geminiKeyVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (geminiKeyVisible) {
                                        "Hide API key"
                                    } else {
                                        "Show API key"
                                    },
                                    tint = PureBlack,
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                            focusedLabelColor = PureBlack,
                            unfocusedLabelColor = PureBlack,
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = PureBlack,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::saveGeminiApiKey,
                            enabled = uiState.geminiApiKey.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyDeep,
                                contentColor = PureWhite,
                            ),
                        ) {
                            Text("Save Key", color = PureWhite)
                        }
                        Button(
                            onClick = viewModel::clearGeminiApiKey,
                            enabled = uiState.geminiKeySource == GeminiApiKeySource.Custom ||
                                uiState.geminiApiKey.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeleteRed,
                                contentColor = PureWhite,
                            ),
                        ) {
                            Text("Clear", color = PureWhite)
                        }
                    }

                    // ——— Learning Settings ———
                    SettingsSectionTitle("Learning Settings")
                    val learningSettings by viewModel.learningSettings.collectAsState()
                    LearningSettingsCard(
                        settings = learningSettings,
                        onSave = viewModel::saveLearningSettings,
                    )

                    // ——— Motion Settings ———
                    SettingsSectionTitle("Motion Settings")
                    val motionSettings by viewModel.motionSettings.collectAsState()
                    MotionSettingsCard(
                        settings = motionSettings,
                        onSave = viewModel::saveMotionSettings,
                    )

                    // ——— Storage Settings ———
                    SettingsSectionTitle("Storage Settings")
                    if (uiState.permissionRequired) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OffWhite),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Permission Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureBlack,
                                )
                                Text(
                                    text = "Drive access needs your consent. The permission screen should open — grant access, then connection will be validated again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureBlack,
                                )
                            }
                        }
                    }
                    if (uiState.isLoading || uiState.isValidating) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricBlue,
                        )
                    }
                    OutlinedTextField(
                        value = uiState.folderId,
                        onValueChange = viewModel::updateFolderId,
                        label = { Text("Data folder ID (Root)", color = PureBlack) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && !uiState.isValidating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PureBlack,
                            unfocusedTextColor = PureBlack,
                            focusedLabelColor = PureBlack,
                            unfocusedLabelColor = PureBlack,
                            focusedBorderColor = NavyDeep,
                            unfocusedBorderColor = PureBlack,
                        ),
                    )

                    Button(
                        onClick = viewModel::saveFolderId,
                        enabled = !uiState.isValidating && !uiState.isResetting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isLoading) "Saving…" else "Set Drive Folder", color = PureWhite)
                    }

                    Button(
                        onClick = viewModel::validateConnection,
                        enabled = !uiState.isLoading && !uiState.isResetting && !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isValidating) "Validating…" else "Validate Connection", color = PureWhite)
                    }

                    Button(
                        onClick = viewModel::backupToDrive,
                        enabled = !uiState.isLoading && !uiState.isValidating && !uiState.isResetting && !uiState.isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isBackingUp) "Backing up…" else "Backup to Drive", color = PureWhite)
                    }

                    Button(
                        onClick = viewModel::openRestorePicker,
                        enabled = !uiState.isLoading && !uiState.isValidating && !uiState.isResetting && !uiState.isBackingUp,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isRestoring) "Restoring…" else "Restore from Drive", color = PureWhite)
                    }

                    val lastBrainSync by viewModel.lastBrainSyncAt.collectAsState()
                    Button(
                        onClick = viewModel::syncBrainNow,
                        enabled = !uiState.isSyncingBrain && !uiState.isRestoring && !uiState.isResetting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                    ) {
                        if (uiState.isSyncingBrain) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isSyncingBrain) "Syncing Brain…" else "Sync Brain now", color = PureWhite)
                    }
                    Text(
                        text = if (lastBrainSync > 0) "Brain last synced ${formatBackupTime(lastBrainSync)}" else "Brain not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateGray,
                    )

                    Button(
                        onClick = viewModel::showPasswordDialog,
                        enabled = !uiState.isLoading && !uiState.isValidating && !uiState.isResetting && !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeleteRed,
                            contentColor = PureWhite
                        ),
                    ) {
                        if (uiState.isResetting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Reset Database", color = PureWhite)
                    }
            }
        }

        // Password dialog
        if (uiState.showPasswordDialog) {
            var password by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissPasswordDialog,
                title = {
                    Text("Enter Password", color = PureBlack)
                },
                text = {
                    Column {
                        Text(
                            "Please enter the password to reset the database.",
                            color = PureBlack,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password", color = PureBlack) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PureBlack,
                                unfocusedTextColor = PureBlack,
                                focusedLabelColor = PureBlack,
                                unfocusedLabelColor = PureBlack,
                                focusedBorderColor = DeleteRed,
                                unfocusedBorderColor = PureBlack,
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.verifyPasswordAndShowConfirmation(password)
                            password = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = DeleteRed
                        ),
                        enabled = password.isNotBlank()
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissPasswordDialog()
                            password = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PureBlack
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        // Reset confirmation dialog
        if (uiState.showResetConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::dismissResetConfirmation,
                title = {
                    Text("Reset Database", color = PureBlack)
                },
                text = {
                    Text(
                        "This will delete all daily data from the database (food, habits, activities, exercise, self text/audio, and home entries). " +
                        "Self photo albums and their Drive links are kept so images in Google Drive stay connected. " +
                        "Sleep targets, notifications, activity labels, and active presets on this device are not removed. " +
                        "Presets and Lists in Drive will not be affected.\n\n" +
                        "This action cannot be undone.",
                        color = PureBlack
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::resetDatabase,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = DeleteRed
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissResetConfirmation,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PureBlack
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        if (uiState.showRestorePicker) {
            RestorePickerDialog(
                isLoading = uiState.isLoadingSnapshots,
                snapshots = uiState.backupSnapshots,
                onPick = { pendingRestore = it },
                onDismiss = viewModel::dismissRestorePicker,
            )
        }

        pendingRestore?.let { snapshot ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("Restore \"${snapshot.name}\"?", color = PureBlack) },
                text = {
                    Text(
                        "This replaces all data on this device with the backup from ${formatBackupTime(snapshot.createdAt)}. " +
                            "Anything logged since then will be lost, and the app restarts.\n\n" +
                            "Dated backups on Drive are kept, so you can restore a different one later.",
                        color = PureBlack
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingRestore = null
                            viewModel.restoreFromDrive(snapshot)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = DeleteRed)
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingRestore = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = PureBlack)
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        // Preset overwrite modal
        if (uiState.showPresetOverwriteModal && uiState.presetModalCategory != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPresetModal,
                title = { Text("Apply preset \"${uiState.presetModalDisplayName}\"", color = PureBlack) },
                text = {
                    val categoryName = uiState.presetModalCategory!!.name
                    val newLabelsNote = if (uiState.presetModalNewLabels.isNotEmpty()) {
                        "\n\nNew activity labels will be created: ${uiState.presetModalNewLabels.joinToString(", ")}."
                    } else {
                        ""
                    }
                    Text(
                        "Save this file as the active $categoryName preset. Days without saved " +
                            "$categoryName data will use it; saved days are not changed.$newLabelsNote",
                        color = PureBlack
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.applyPresetFromModal() },
                        enabled = !uiState.isPresetApplying,
                        colors = ButtonDefaults.textButtonColors(contentColor = NavyDeep)
                    ) {
                        if (uiState.isPresetApplying) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = NavyDeep, strokeWidth = 2.dp)
                        } else {
                            Text("Save preset")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissPresetModal,
                        enabled = !uiState.isPresetApplying,
                        colors = ButtonDefaults.textButtonColors(contentColor = PureBlack)
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        if (showCreateActivityLabel) {
            CreateActivityLabelDialog(
                name = newActivityLabelName,
                colorArgb = newActivityLabelColor,
                onNameChange = { newActivityLabelName = it },
                onColorChange = { newActivityLabelColor = it },
                onDismiss = { showCreateActivityLabel = false },
                onSave = {
                    val trimmed = newActivityLabelName.trim()
                    if (trimmed.isBlank()) return@CreateActivityLabelDialog
                    viewModel.createActivityLabel(trimmed, newActivityLabelColor) {
                        showCreateActivityLabel = false
                        newActivityLabelName = ""
                    }
                },
            )
        }

        pendingDeleteActivityLabel?.let { label ->
            AlertDialog(
                onDismissRequest = { pendingDeleteActivityLabel = null },
                containerColor = PureWhite,
                title = { Text("Delete label?", color = PureBlack) },
                text = {
                    Text(
                        "Remove \"${label.name}\"? Activities using it will also be removed.",
                        color = PureBlack,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteActivityLabel(label.id)
                        pendingDeleteActivityLabel = null
                    }) { Text("Delete", color = DeleteRed) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteActivityLabel = null }) {
                        Text("Cancel", color = PureBlack)
                    }
                },
            )
        }

        uiState.showSleepTimePicker?.let { kind ->
            SettingsTimePickerDialog(
                initialMinutes = when (kind) {
                    SleepPickerKind.Bedtime -> uiState.sleepBedtimeMinutes
                    SleepPickerKind.Wakeup -> uiState.sleepWakeupMinutes
                },
                onDismiss = { viewModel.showSleepTimePicker(null) },
                onConfirm = { minutes ->
                    when (kind) {
                        SleepPickerKind.Bedtime -> viewModel.setSleepBedtime(minutes)
                        SleepPickerKind.Wakeup -> viewModel.setSleepWakeup(minutes)
                    }
                    viewModel.showSleepTimePicker(null)
                }
            )
        }

        if (uiState.showFilloutTimePicker) {
            SettingsTimePickerDialog(
                initialMinutes = uiState.filloutReminderMinutes ?: 20 * 60,
                onDismiss = { viewModel.showFilloutTimePicker(false) },
                onConfirm = { minutes -> viewModel.setFilloutReminder(minutes) }
            )
        }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, isFirstSection: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            top = if (isFirstSection) 0.dp else 16.dp,
            bottom = 8.dp
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NavyDeep
        )
        HorizontalDivider(color = NavyDeep, thickness = 2.dp)
    }
}

@Composable
private fun LearningSettingsCard(
    settings: LearningSettings,
    onSave: (LearningSettings) -> Unit,
) {
    var onHoldDays by remember(settings) { mutableStateOf(settings.onHoldDays.toString()) }
    var intervals by remember(settings) { mutableStateOf(settings.reviewIntervalsDays.joinToString(", ")) }
    var quizSize by remember(settings) { mutableStateOf(settings.quizSize.toString()) }
    var passPercent by remember(settings) { mutableStateOf(settings.passPercent.toString()) }
    val parsedIntervals = intervals.split(",").mapNotNull { it.trim().toIntOrNull() }
    val valid = onHoldDays.toIntOrNull() != null && parsedIntervals.isNotEmpty() &&
        quizSize.toIntOrNull() != null && passPercent.toIntOrNull() != null
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = PureBlack,
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = PureBlack,
    )
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    Card(
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "In Progress tiles with no activity go On Hold; Learned tiles come back for review on an expanding schedule. " +
                    "After the last interval a tile is mastered.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
            )
            OutlinedTextField(
                value = onHoldDays,
                onValueChange = { onHoldDays = it.filter(Char::isDigit).take(3) },
                label = { Text("On Hold after (days)") },
                singleLine = true,
                keyboardOptions = numberKeyboard,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = intervals,
                onValueChange = { intervals = it.filter { c -> c.isDigit() || c == ',' || c == ' ' } },
                label = { Text("Review intervals (days)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quizSize,
                    onValueChange = { quizSize = it.filter(Char::isDigit).take(2) },
                    label = { Text("Quiz questions") },
                    singleLine = true,
                    keyboardOptions = numberKeyboard,
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = passPercent,
                    onValueChange = { passPercent = it.filter(Char::isDigit).take(3) },
                    label = { Text("Pass (%)") },
                    singleLine = true,
                    keyboardOptions = numberKeyboard,
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            LearningSettings(
                                onHoldDays = onHoldDays.toInt(),
                                reviewIntervalsDays = parsedIntervals,
                                quizSize = quizSize.toInt(),
                                passPercent = passPercent.toInt(),
                            )
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) { Text("Save", color = PureWhite) }
                TextButton(
                    onClick = { onSave(LearningSettings()) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset defaults", color = NavyDeep) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotionSettingsCard(
    settings: MotionSettings,
    onSave: (MotionSettings) -> Unit,
) {
    var dailyTarget by remember(settings) { mutableStateOf(settings.dailyTarget.toString()) }
    var supermarketDay by remember(settings) { mutableStateOf(settings.supermarketDay) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PureBlack,
        unfocusedTextColor = PureBlack,
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = PureBlack,
        focusedBorderColor = NavyDeep,
        unfocusedBorderColor = PureBlack,
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Points you aim for each day, and the day the weekly supermarket trip is due " +
                    "(20 pts on time, 5 fewer for each day late).",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
            )
            OutlinedTextField(
                value = dailyTarget,
                onValueChange = { dailyTarget = it.filter(Char::isDigit).take(4) },
                label = { Text("Daily target (points)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Supermarket day", style = MaterialTheme.typography.labelLarge, color = PureBlack)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day == supermarketDay
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) NavyDeep else PureWhite)
                            .clickable { supermarketDay = day }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US),
                            color = if (selected) PureWhite else PureBlack,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(MotionSettings(dailyTarget = dailyTarget.toInt(), supermarketDay = supermarketDay))
                    },
                    enabled = dailyTarget.toIntOrNull() != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) { Text("Save", color = PureWhite) }
                TextButton(
                    onClick = { onSave(MotionSettings()) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset defaults", color = NavyDeep) }
            }
        }
    }
}

@Composable
private fun AboutVectorCard() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PureWhite),
                ) {
                    Image(
                        painter = painterResource(R.drawable.dark_logo),
                        contentDescription = "Vector logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.6f),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Vector",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyDeep,
                    )
                    if (versionName != null) {
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray,
                        )
                    }
                }
            }
            Text(
                text = "Vector is a personal life-tracking app for logging routines, food, exercise, " +
                    "sleep, habits, and self-reflection in one place — with a Brain for notes and tasks, " +
                    "a WearOS companion for workouts, and Google Drive sync to keep your data yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = PureBlack,
            )
            Text(
                text = "Created by Victor Macedo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = NavyDeep,
            )
        }
    }
}

@Composable
private fun TimeChip(minutes: Int, onClick: () -> Unit) {
    val totalMin = (minutes % (24 * 60) + 24 * 60) % (24 * 60)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = PureWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, PureBlack.copy(alpha = 0.3f)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("%02d:%02d".format(totalMin / 60, totalMin % 60), style = MaterialTheme.typography.bodyLarge, color = PureBlack)
        }
    }
}

@Composable
private fun AlarmReminderTile(label: String, timeText: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PureWhite).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = PureBlack)
            Text(timeText, style = MaterialTheme.typography.bodyMedium, color = SlateGray)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = DeleteRed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    val totalMinutes = 24 * 60
    val normalized = (initialMinutes % totalMinutes + totalMinutes) % totalMinutes
    val state = rememberTimePickerState(initialHour = normalized / 60, initialMinute = normalized % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK", color = NavyDeep) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SlateGray) } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = PureWhite,
                    clockDialSelectedContentColor = NavyDeep,
                    clockDialUnselectedContentColor = NavyDeep,
                    containerColor = PureWhite,
                    selectorColor = NavyDeep,
                    timeSelectorSelectedContainerColor = Color(0xFFE0E0E0),
                    timeSelectorUnselectedContainerColor = PureWhite,
                    timeSelectorSelectedContentColor = NavyDeep,
                    timeSelectorUnselectedContentColor = NavyDeep
                )
            )
        },
        containerColor = PureWhite
    )
}

@Composable
private fun PresetTile(
    label: String,
    activeFileName: String?,
    presetJsonName: String? = null,
    onBrowse: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = PureBlack,
            )
            Text(
                text = activeFileName ?: "No preset",
                style = MaterialTheme.typography.bodyMedium,
                color = if (activeFileName != null) PureBlack else SlateGray,
                modifier = Modifier.fillMaxWidth(),
            )
            if (presetJsonName != null) {
                Text(
                    text = presetJsonName,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onBrowse,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) {
                    Text("Browse", color = PureWhite)
                }
                if (activeFileName != null) {
                    Button(
                        onClick = onRemove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = PureWhite),
                    ) {
                        Text("Remove", color = PureWhite)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun RoutineLabelsSettingsSection(
    labels: List<ActivityLabel>,
    onAddClick: () -> Unit,
    onDeleteLabel: (ActivityLabel) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Routine labels",
                    style = MaterialTheme.typography.labelLarge,
                    color = PureBlack,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add label", tint = NavyDeep)
                }
            }
            Text(
                text = "Labels for routine activities. Long-press to delete. Kept when resetting the database.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
            )
            if (labels.isEmpty()) {
                Text("No labels yet", color = SlateGray, style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    labels.forEach { label ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(label.colorArgb),
                            border = BorderStroke(1.dp, PureBlack.copy(alpha = 0.25f)),
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (!label.isSystem) onDeleteLabel(label)
                                },
                            ),
                        ) {
                            Text(
                                text = label.name,
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
