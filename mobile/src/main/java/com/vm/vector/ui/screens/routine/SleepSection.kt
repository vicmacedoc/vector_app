package com.vm.vector.ui.screens.routine

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vm.core.models.SleepEntry
import com.vm.core.models.SleepMath
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PriorityHigh
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.data.GeminiSleepClient
import com.vm.vector.data.SleepStage
import com.vm.vector.ui.components.DialogMultilineFieldDefaults
import com.vm.vector.ui.components.KeyboardAwareDialogColumn
import com.vm.vector.util.applyExifOrientationToJpegBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun sleepArcsForCalendarDay(
    previousNight: SleepEntry?,
    tonight: SleepEntry?,
    defaultBedMinutes: Int,
    defaultWakeMinutes: Int,
    useActual: Boolean,
): List<SleepClockArc> {
    val arcs = mutableListOf<SleepClockArc>()
    val defaultBed = SleepMath.formatHhMm(defaultBedMinutes)
    val defaultWake = SleepMath.formatHhMm(defaultWakeMinutes)
    val wakeHhMm = if (useActual) {
        previousNight?.actualWakeUp ?: previousNight?.plannedWakeUp ?: defaultWake
    } else {
        previousNight?.plannedWakeUp ?: defaultWake
    }
    SleepMath.parseHhMm(wakeHhMm)?.let { wake ->
        if (wake > 0) arcs += SleepClockArc(0, wake)
    }
    val bedHhMm = if (useActual) {
        tonight?.actualBedtime ?: tonight?.plannedBedtime ?: defaultBed
    } else {
        tonight?.plannedBedtime ?: defaultBed
    }
    SleepMath.parseHhMm(bedHhMm)?.let { bed ->
        if (bed >= 12 * 60) arcs += SleepClockArc(bed, SleepMath.MINUTES_PER_DAY)
    }
    return arcs
}

private enum class SleepClockTarget {
    PrevPlannedBed, PrevActualBed,
    PrevPlannedWake, PrevActualWake,
    TonightPlannedBed, TonightActualBed,
}

@Composable
fun SleepSection(
    enabled: Boolean,
    previousNight: SleepEntry?,
    tonight: SleepEntry?,
    defaultBedMinutes: Int,
    defaultWakeMinutes: Int,
    onSetPlannedBedtime: (nightDate: String, minutes: Int) -> Unit,
    onSetPlannedWakeUp: (nightDate: String, minutes: Int) -> Unit,
    onSetActualBedtime: (nightDate: String, minutes: Int) -> Unit,
    onSetActualWakeUp: (nightDate: String, minutes: Int) -> Unit,
    onSetFellAsleep: (nightDate: String, minutes: Int?) -> Unit,
    onSetLatency: (nightDate: String, minutes: Int?) -> Unit,
    onSetStageMinutes: (nightDate: String, stage: SleepStage, minutes: Int?) -> Unit,
    onSetStagePercent: (nightDate: String, stage: SleepStage, percent: Int?) -> Unit,
    onApplyAi: (nightDate: String, estimate: GeminiSleepClient.SleepEstimate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultBed = SleepMath.formatHhMm(defaultBedMinutes)
    val defaultWake = SleepMath.formatHhMm(defaultWakeMinutes)
    val prevDate = previousNight?.date
    val tonightDate = tonight?.date

    val plannedPrevBed = previousNight?.plannedBedtime ?: defaultBed
    val actualPrevBed = previousNight?.actualBedtime
    val plannedWake = previousNight?.plannedWakeUp ?: defaultWake
    val actualWake = previousNight?.actualWakeUp
    val plannedTonightBed = tonight?.plannedBedtime ?: defaultBed
    val actualTonightBed = tonight?.actualBedtime

    val displayPrevBed = actualPrevBed ?: plannedPrevBed
    val displayWake = actualWake ?: plannedWake
    val displayTonightBed = actualTonightBed ?: plannedTonightBed

    val sleepMins = SleepMath.durationMinutes(displayPrevBed, displayWake)
    val awakeMins = SleepMath.durationMinutes(displayWake, displayTonightBed)

    var clockTarget by remember { mutableStateOf<SleepClockTarget?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sleep",
                color = PureBlack,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (detailsExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (detailsExpanded) "Hide sleep details" else "Show sleep details",
                    tint = NavyDeep,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            SleepTimeColumn(
                title = "Bedtime",
                plannedHhMm = plannedPrevBed,
                actualDisplay = displayPrevBed,
                matchesPlanned = actualPrevBed == null || SleepMath.timesMatch(actualPrevBed, plannedPrevBed),
                enabled = enabled && prevDate != null,
                onPlannedClick = { clockTarget = SleepClockTarget.PrevPlannedBed },
                onActualClick = { clockTarget = SleepClockTarget.PrevActualBed },
                modifier = Modifier.weight(1f),
            )
            SleepConnector(
                icon = Icons.Default.DarkMode,
                iconTint = SlateGray,
                duration = SleepMath.formatDuration(sleepMins),
                modifier = Modifier.weight(0.85f),
            )
            SleepTimeColumn(
                title = "Wake-up",
                plannedHhMm = plannedWake,
                actualDisplay = displayWake,
                matchesPlanned = actualWake == null || SleepMath.timesMatch(actualWake, plannedWake),
                enabled = enabled && prevDate != null,
                onPlannedClick = { clockTarget = SleepClockTarget.PrevPlannedWake },
                onActualClick = { clockTarget = SleepClockTarget.PrevActualWake },
                modifier = Modifier.weight(1f),
            )
            SleepConnector(
                emoji = "☀️",
                duration = SleepMath.formatDuration(awakeMins),
                modifier = Modifier.weight(0.85f),
            )
            SleepTimeColumn(
                title = "Bedtime",
                plannedHhMm = plannedTonightBed,
                actualDisplay = displayTonightBed,
                matchesPlanned = actualTonightBed == null || SleepMath.timesMatch(actualTonightBed, plannedTonightBed),
                enabled = enabled && tonightDate != null,
                onPlannedClick = { clockTarget = SleepClockTarget.TonightPlannedBed },
                onActualClick = { clockTarget = SleepClockTarget.TonightActualBed },
                modifier = Modifier.weight(1f),
            )
        }
        if (detailsExpanded) {
            Spacer(modifier = Modifier.height(16.dp))
            SleepDetailsBlock(
                entry = previousNight,
                onClick = { if (enabled && prevDate != null) showDetails = true },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { if (enabled && prevDate != null) showAi = true },
                enabled = enabled && prevDate != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) {
                Text("Fill from AI")
            }
        }
    }

    clockTarget?.let { target ->
        val initial = when (target) {
            SleepClockTarget.PrevPlannedBed -> SleepMath.parseHhMm(plannedPrevBed) ?: defaultBedMinutes
            SleepClockTarget.PrevActualBed -> SleepMath.parseHhMm(displayPrevBed) ?: defaultBedMinutes
            SleepClockTarget.PrevPlannedWake -> SleepMath.parseHhMm(plannedWake) ?: defaultWakeMinutes
            SleepClockTarget.PrevActualWake -> SleepMath.parseHhMm(displayWake) ?: defaultWakeMinutes
            SleepClockTarget.TonightPlannedBed -> SleepMath.parseHhMm(plannedTonightBed) ?: defaultBedMinutes
            SleepClockTarget.TonightActualBed -> SleepMath.parseHhMm(displayTonightBed) ?: defaultBedMinutes
        }
        ActivityTimePickerDialog(
            initialMinutes = initial,
            onDismiss = { clockTarget = null },
            onConfirm = { minutes ->
                when (target) {
                    SleepClockTarget.PrevPlannedBed -> prevDate?.let { onSetPlannedBedtime(it, minutes) }
                    SleepClockTarget.PrevActualBed -> prevDate?.let { onSetActualBedtime(it, minutes) }
                    SleepClockTarget.PrevPlannedWake -> prevDate?.let { onSetPlannedWakeUp(it, minutes) }
                    SleepClockTarget.PrevActualWake -> prevDate?.let { onSetActualWakeUp(it, minutes) }
                    SleepClockTarget.TonightPlannedBed -> tonightDate?.let { onSetPlannedBedtime(it, minutes) }
                    SleepClockTarget.TonightActualBed -> tonightDate?.let { onSetActualBedtime(it, minutes) }
                }
                clockTarget = null
            },
        )
    }

    if (showDetails && prevDate != null) {
        SleepDetailsDialog(
            entry = previousNight ?: SleepEntry(date = prevDate),
            onDismiss = { showDetails = false },
            onFellAsleep = { onSetFellAsleep(prevDate, it) },
            onLatency = { onSetLatency(prevDate, it) },
            onStageMinutes = { stage, mins -> onSetStageMinutes(prevDate, stage, mins) },
            onStagePercent = { stage, pct -> onSetStagePercent(prevDate, stage, pct) },
        )
    }

    if (showAi && prevDate != null) {
        FillSleepFromAiDialog(
            onDismiss = { showAi = false },
            onFilled = { estimate ->
                onApplyAi(prevDate, estimate)
                showAi = false
            },
        )
    }
}

@Composable
private fun SleepTimeColumn(
    title: String,
    plannedHhMm: String,
    actualDisplay: String,
    matchesPlanned: Boolean,
    enabled: Boolean,
    onPlannedClick: () -> Unit,
    onActualClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = PureBlack, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            text = "($plannedHhMm)",
            color = SlateGray,
            fontSize = 11.sp,
            modifier = Modifier.clickable(enabled = enabled, onClick = onPlannedClick),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(
                    width = 2.dp,
                    color = if (matchesPlanned) PriorityLow else PriorityHigh,
                    shape = RoundedCornerShape(8.dp),
                )
                .clip(RoundedCornerShape(8.dp))
                .background(PureWhite)
                .clickable(enabled = enabled, onClick = onActualClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(actualDisplay, color = PureBlack, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SleepConnector(
    duration: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = SlateGray,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        } else if (emoji != null) {
            Text(emoji, fontSize = 16.sp)
        }
        Text(
            text = duration,
            color = SlateGray,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun SleepDetailsBlock(
    entry: SleepEntry?,
    onClick: () -> Unit,
) {
    val total = entry?.let { SleepMath.totalSleepMinutes(it) }
    val latency = entry?.let { SleepMath.latencyMinutes(it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffWhite)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailLine("Feel asleep at", clockAndAfter(entry?.fellAsleepAt, latency))
        DetailLine("Deep state", stageLine(entry?.deepMinutes, total))
        DetailLine("Light state", stageLine(entry?.lightMinutes, total))
        DetailLine("REM state", stageLine(entry?.remMinutes, total))
        DetailLine("Awake state", stageLine(entry?.awakeMinutes, total))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = PureBlack, fontSize = 13.sp)
        Text(value, color = SlateGray, fontSize = 13.sp)
    }
}

private fun clockAndAfter(fellAsleep: String?, latency: Int?): String {
    val clock = fellAsleep ?: "--"
    val after = latency?.let { "After ${SleepMath.formatDuration(it)}" } ?: "After --"
    return "$clock · $after"
}

private fun stageLine(minutes: Int?, total: Int?): String {
    if (minutes == null) return "--"
    val pct = SleepMath.stagePercent(minutes, total)
    val pctText = pct?.let { "$it%" } ?: "--"
    return "${SleepMath.formatDuration(minutes)} · $pctText"
}

@Composable
fun SleepDetailsDialog(
    entry: SleepEntry,
    onDismiss: () -> Unit,
    onFellAsleep: (Int?) -> Unit,
    onLatency: (Int?) -> Unit,
    onStageMinutes: (SleepStage, Int?) -> Unit,
    onStagePercent: (SleepStage, Int?) -> Unit,
) {
    val total = SleepMath.totalSleepMinutes(entry)
    val latency = SleepMath.latencyMinutes(entry)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding()
                .heightIn(max = 560.dp),
        ) {
            KeyboardAwareDialogColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Sleep details", color = PureBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                LinkedPairRow(
                    leftLabel = "Fell asleep",
                    leftValue = entry.fellAsleepAt ?: "",
                    leftPlaceholder = "HH:mm",
                    rightLabel = "After (min)",
                    rightValue = latency?.toString() ?: "",
                    onLeft = { text ->
                        if (text.isBlank()) onFellAsleep(null)
                        else SleepMath.parseHhMm(text)?.let { onFellAsleep(it) }
                    },
                    onRight = { text ->
                        if (text.isBlank()) onLatency(null)
                        else text.toIntOrNull()?.let { onLatency(it) }
                    },
                )
                StagePairRow("Deep", SleepStage.DEEP, entry.deepMinutes, total, onStageMinutes, onStagePercent)
                StagePairRow("Light", SleepStage.LIGHT, entry.lightMinutes, total, onStageMinutes, onStagePercent)
                StagePairRow("REM", SleepStage.REM, entry.remMinutes, total, onStageMinutes, onStagePercent)
                StagePairRow("Awake", SleepStage.AWAKE, entry.awakeMinutes, total, onStageMinutes, onStagePercent)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = NavyDeep)
                }
            }
        }
    }
}

@Composable
private fun LinkedPairRow(
    leftLabel: String,
    leftValue: String,
    leftPlaceholder: String,
    rightLabel: String,
    rightValue: String,
    onLeft: (String) -> Unit,
    onRight: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = leftValue,
            onValueChange = onLeft,
            label = { Text(leftLabel) },
            placeholder = { Text(leftPlaceholder) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = sleepFieldColors(),
        )
        OutlinedTextField(
            value = rightValue,
            onValueChange = onRight,
            label = { Text(rightLabel) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = sleepFieldColors(),
        )
    }
}

@Composable
private fun StagePairRow(
    label: String,
    stage: SleepStage,
    minutes: Int?,
    total: Int?,
    onMinutes: (SleepStage, Int?) -> Unit,
    onPercent: (SleepStage, Int?) -> Unit,
) {
    val pct = SleepMath.stagePercent(minutes, total)
    LinkedPairRow(
        leftLabel = "$label (min)",
        leftValue = minutes?.toString() ?: "",
        leftPlaceholder = "--",
        rightLabel = "$label (%)",
        rightValue = pct?.toString() ?: "",
        onLeft = { text ->
            if (text.isBlank()) onMinutes(stage, null) else text.toIntOrNull()?.let { onMinutes(stage, it) }
        },
        onRight = { text ->
            if (text.isBlank()) onMinutes(stage, null) else text.toIntOrNull()?.let { onPercent(stage, it) }
        },
    )
}

@Composable
private fun sleepFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NavyDeep,
    unfocusedBorderColor = NavyDeep,
    focusedTextColor = PureBlack,
    unfocusedTextColor = PureBlack,
    cursorColor = NavyDeep,
    focusedLabelColor = NavyDeep,
    unfocusedLabelColor = SlateGray,
)

@Composable
fun FillSleepFromAiDialog(
    onDismiss: () -> Unit,
    onFilled: (GeminiSleepClient.SleepEstimate) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { GeminiSleepClient(context.applicationContext) }

    var description by remember { mutableStateOf("") }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imagePreview by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun setImageFromBytes(bytes: ByteArray) {
        imageBytes = bytes
        imagePreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (!success || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (raw == null) {
                    errorMessage = "Failed to read captured photo"
                    return@launch
                }
                val oriented = applyExifOrientationToJpegBytes(raw) ?: raw
                setImageFromBytes(oriented)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load photo"
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCaptureUri?.let { takePictureLauncher.launch(it) }
        } else {
            pendingCaptureUri = null
            errorMessage = "Camera permission denied"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    errorMessage = "Failed to read selected image"
                    return@launch
                }
                val oriented = applyExifOrientationToJpegBytes(bytes) ?: bytes
                setImageFromBytes(oriented)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load image"
            }
        }
    }

    fun startCameraCapture() {
        val file = File(context.cacheDir, "sleep_ai_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureUri = uri
        when {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> takePictureLauncher.launch(uri)
            else -> cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun sendToAi() {
        if (isLoading) return
        if (description.isBlank() && imageBytes == null) {
            errorMessage = "Add a description and/or a screenshot"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = client.estimateSleep(
                description = description.takeIf { it.isNotBlank() },
                imageBytes = imageBytes,
            )
            isLoading = false
            result.onSuccess { onFilled(it) }
                .onFailure { e -> errorMessage = e.message ?: "AI estimate failed" }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        modifier = Modifier.imePadding(),
        title = { Text("Fill from AI", color = PureBlack) },
        text = {
            KeyboardAwareDialogColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste notes or upload a screenshot from Nest Hub, Health, or another sleep app. Only fields you provide are changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateGray,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { startCameraCapture() },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera")
                    }
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload")
                    }
                }
                imagePreview?.let { bmp ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OffWhite)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Sleep screenshot",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = {
                                imageBytes = null
                                imagePreview = null
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                            enabled = !isLoading,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = PureWhite)
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional with photo)", color = PureBlack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = DialogMultilineFieldDefaults.MediumMin,
                            max = DialogMultilineFieldDefaults.MediumMax,
                        ),
                    minLines = 3,
                    enabled = !isLoading,
                    colors = sleepFieldColors(),
                )
                errorMessage?.let { msg ->
                    Text(msg, color = DeleteRed, style = MaterialTheme.typography.bodySmall)
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = NavyDeep, strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { sendToAi() }, enabled = !isLoading) {
                Text("Fill", color = NavyDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = SlateGray)
            }
        },
        containerColor = PureWhite,
    )
}
