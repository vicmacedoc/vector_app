package com.vm.vector.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.VectorApplication
import com.vm.vector.data.sync.BrainMerge
import com.vm.vector.data.sync.BrainStateSync
import com.vm.vector.data.sync.BrainSyncFiles
import com.vm.vector.data.sync.BrainSyncOutcome
import com.vm.vector.data.sync.ConflictKind
import com.vm.vector.data.sync.DailyShards
import com.vm.vector.data.sync.MergeAnalysis
import com.vm.vector.data.sync.MergeChoice
import com.vm.vector.data.sync.MergeConflict
import com.vm.vector.data.sync.PendingMergeView
import com.vm.vector.data.sync.Resolution
import com.vm.vector.data.sync.Side
import com.vm.vector.data.sync.SyncDevice
import com.vm.vector.ui.backup.formatBackupTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

private enum class MergeStep { OVERVIEW, CONFLICTS }

private val HairlineGray = SlateGray.copy(alpha = 0.25f)

/** What the merge sheet shows; Brain and the daily shards both fill it. */
data class MergeSheetView(
    val title: String,
    val analysis: MergeAnalysis,
    val remoteDevice: SyncDevice?,
    val remoteWrittenAt: Long,
)

sealed interface MergeApplyResult {
    object Done : MergeApplyResult
    /** The other device synced again while the user was choosing; reload the view. */
    object Changed : MergeApplyResult
    data class Failed(val message: String) : MergeApplyResult
}

/**
 * Shown anywhere in the app while Brain has a pending merge (both this phone and another device changed it).
 * "Later" hides it until the next sync finds the merge still pending.
 */
@Composable
fun BrainMergeHost() {
    val context = LocalContext.current
    val app = context.applicationContext as VectorApplication
    val files = remember { BrainSyncFiles(app) }
    val sync = remember { BrainStateSync(app, app.database) }
    val hasPending by BrainSyncFiles.hasPending.collectAsState()
    var dismissed by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<PendingMergeView?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { files.refreshPendingFlag() } }
    LaunchedEffect(hasPending, reloadKey) {
        if (hasPending) dismissed = false
        view = if (hasPending) withContext(Dispatchers.IO) { sync.loadPending() } else null
    }

    val current = view
    if (!hasPending || dismissed || current == null) return

    MergeSheet(
        view = MergeSheetView("Brain changed on two devices", current.analysis, current.remoteDevice, current.remoteWrittenAt),
        onApply = { choice, choices ->
            when (val outcome = withContext(Dispatchers.IO) { sync.resolvePending(choice, choices) }) {
                is BrainSyncOutcome.Pushed, BrainSyncOutcome.UpToDate, is BrainSyncOutcome.Pulled -> {
                    view = null
                    MergeApplyResult.Done
                }
                is BrainSyncOutcome.NeedsDecision -> {
                    reloadKey++
                    MergeApplyResult.Changed
                }
                is BrainSyncOutcome.Failed -> MergeApplyResult.Failed(outcome.message)
                BrainSyncOutcome.NotConfigured -> MergeApplyResult.Failed("Connect Google Drive in Settings first.")
            }
        },
        onLater = { dismissed = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeSheet(
    view: MergeSheetView,
    onApply: suspend (MergeChoice, Map<String, Resolution>) -> MergeApplyResult,
    onLater: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember(view) { mutableStateOf(MergeStep.OVERVIEW) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmWhole by remember { mutableStateOf<MergeChoice?>(null) }
    val choices = remember(view) {
        mutableStateMapOf<String, Resolution>().apply {
            view.analysis.conflicts.forEach { put(it.id, if (it.newer == Side.REMOTE) Resolution.REMOTE else Resolution.LOCAL) }
        }
    }
    val otherName = deviceLabel(view.remoteDevice)

    fun apply(choice: MergeChoice) {
        busy = true
        message = null
        scope.launch {
            val result = onApply(choice, choices.toMap())
            busy = false
            when (result) {
                MergeApplyResult.Done -> Unit
                MergeApplyResult.Changed -> message = "$otherName synced again while you were choosing. Review the updated changes."
                is MergeApplyResult.Failed -> message = result.message
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = sheetState,
        containerColor = PureWhite,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                MergeStep.OVERVIEW -> Overview(
                    view = view,
                    otherName = otherName,
                    busy = busy,
                    onSmart = {
                        if (view.analysis.conflicts.isEmpty()) apply(MergeChoice.SMART) else step = MergeStep.CONFLICTS
                    },
                    onKeepLocal = { confirmWhole = MergeChoice.KEEP_LOCAL },
                    onKeepRemote = { confirmWhole = MergeChoice.KEEP_REMOTE },
                    onLater = onLater,
                )
                MergeStep.CONFLICTS -> ConflictReview(
                    view = view,
                    otherName = otherName,
                    choices = choices,
                    busy = busy,
                    onBack = { step = MergeStep.OVERVIEW },
                    onApply = { apply(MergeChoice.SMART) },
                )
            }
            message?.let { Text(it, color = DeleteRed, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(8.dp))
        }
    }

    confirmWhole?.let { choice ->
        val (title, body) = when (choice) {
            MergeChoice.KEEP_LOCAL -> "Keep this phone?" to
                "${view.analysis.remoteChanges} change(s) made on $otherName will be undone on every device."
            else -> "Keep $otherName?" to
                "${view.analysis.localChanges} change(s) made on this phone will be undone."
        }
        AlertDialog(
            onDismissRequest = { confirmWhole = null },
            title = { Text(title, color = PureBlack) },
            text = { Text(body, color = PureBlack) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWhole = null
                        apply(choice)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = DeleteRed),
                ) { Text("Discard them") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmWhole = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = PureBlack),
                ) { Text("Cancel") }
            },
            containerColor = PureWhite,
        )
    }
}

@Composable
private fun ColumnScope.Overview(
    view: MergeSheetView,
    otherName: String,
    busy: Boolean,
    onSmart: () -> Unit,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onLater: () -> Unit,
) {
    val analysis = view.analysis
    Text(view.title, style = MaterialTheme.typography.titleLarge, color = PureBlack)
    Text(
        buildString {
            append("This phone: ${analysis.localChanges} change(s). ")
            append("$otherName: ${analysis.remoteChanges} change(s)")
            if (view.remoteWrittenAt > 0) append(", synced ${formatBackupTime(view.remoteWrittenAt)}")
            append('.')
        },
        color = PureBlack,
    )
    Text(
        if (analysis.conflicts.isEmpty()) {
            "No item was edited on both sides, so Smart merge keeps every change."
        } else {
            "${analysis.conflicts.size} item(s) were edited on both sides. Smart merge keeps everything else and " +
                "lets you pick a side for those (the newer edit is preselected)."
        },
        color = SlateGray,
    )
    Button(
        onClick = onSmart,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
    ) {
        if (busy) {
            CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text(if (analysis.conflicts.isEmpty()) "Smart merge" else "Smart merge · review ${analysis.conflicts.size}")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onKeepLocal,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep),
        ) { Text("Keep this phone", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        OutlinedButton(
            onClick = onKeepRemote,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep),
        ) { Text("Keep $otherName", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    TextButton(
        onClick = onLater,
        enabled = !busy,
        modifier = Modifier.align(Alignment.CenterHorizontally),
        colors = ButtonDefaults.textButtonColors(contentColor = SlateGray),
    ) { Text("Later") }
}

@Composable
private fun ConflictReview(
    view: MergeSheetView,
    otherName: String,
    choices: MutableMap<String, Resolution>,
    busy: Boolean,
    onBack: () -> Unit,
    onApply: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = NavyDeep)) { Text("Back") }
        Text(
            "Pick a side for each item",
            style = MaterialTheme.typography.titleMedium,
            color = PureBlack,
            modifier = Modifier.weight(1f),
        )
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(view.analysis.conflicts, key = { it.id }) { conflict ->
            ConflictCard(
                conflict = conflict,
                view = view,
                otherName = otherName,
                selected = choices[conflict.id] ?: Resolution.LOCAL,
                onSelect = { choices[conflict.id] = it },
            )
        }
    }
    Button(
        onClick = onApply,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
    ) {
        if (busy) {
            CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text("Apply merge")
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: MergeConflict,
    view: MergeSheetView,
    otherName: String,
    selected: Resolution,
    onSelect: (Resolution) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HairlineGray, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${collectionLabel(conflict.collection)} · ${recordTitle(conflict, view)}",
            fontWeight = FontWeight.SemiBold,
            color = PureBlack,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(kindLabel(conflict.kind, otherName), color = SlateGray, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SideColumn(
                label = if (conflict.newer == Side.LOCAL) "This phone · newer" else "This phone",
                record = conflict.local,
                fields = conflict.fields,
                selected = selected == Resolution.LOCAL || selected == Resolution.BOTH,
                onClick = { onSelect(Resolution.LOCAL) },
                modifier = Modifier.weight(1f),
            )
            SideColumn(
                label = if (conflict.newer == Side.REMOTE) "$otherName · newer" else otherName,
                record = conflict.remote,
                fields = conflict.fields,
                selected = selected == Resolution.REMOTE || selected == Resolution.BOTH,
                onClick = { onSelect(Resolution.REMOTE) },
                modifier = Modifier.weight(1f),
            )
        }
        if (conflict.canKeepBoth && conflict.local != null && conflict.remote != null) {
            TextButton(
                onClick = { onSelect(Resolution.BOTH) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selected == Resolution.BOTH) PureWhite else NavyDeep,
                    containerColor = if (selected == Resolution.BOTH) NavyDeep else PureWhite,
                ),
            ) { Text("Keep both (adds a copy)") }
        }
    }
}

@Composable
private fun SideColumn(
    label: String,
    record: JsonObject?,
    fields: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) ElectricBlue else HairlineGray, shape)
            .background(if (selected) ElectricBlue.copy(alpha = 0.06f) else PureWhite, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = NavyDeep, style = MaterialTheme.typography.labelLarge)
        if (record == null) {
            Text("Deleted", color = DeleteRed)
        } else {
            val shown = fields.ifEmpty { summaryFields(record) }
            shown.forEach { field ->
                Text(field, color = SlateGray, style = MaterialTheme.typography.labelSmall)
                Text(
                    display(field, record[field]),
                    color = PureBlack,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.height(2.dp))
    }
}

private fun deviceLabel(device: SyncDevice?): String {
    if (device == null) return "Other device"
    return when {
        device.platform.equals("desktop", ignoreCase = true) -> "Desktop"
        device.name.isNotBlank() -> device.name
        else -> "Other device"
    }
}

private fun collectionLabel(collection: String): String = when (collection) {
    BrainMerge.NODES -> "Tile"
    BrainMerge.NOTES -> "Note"
    BrainMerge.ASSETS -> "File"
    BrainMerge.LEARNING_TILES -> "Learn progress"
    BrainMerge.LEARNING_LINKS -> "Learn link"
    BrainMerge.TAGS -> "Tag"
    BrainMerge.TASKS -> "Task"
    BrainMerge.SCORE_EVENTS -> "Points"
    BrainMerge.SUPERMARKET_ITEMS -> "Supermarket item"
    BrainMerge.META -> "Settings"
    DailyShards.FOOD -> "Food"
    DailyShards.ACTIVITIES -> "Routine activity"
    DailyShards.SLEEP -> "Sleep"
    DailyShards.WORKOUT_SETS -> "Exercise set"
    DailyShards.SELF -> "Self"
    DailyShards.HOME -> "Body & plan"
    DailyShards.HABIT_LOGS -> "Habit check-in"
    DailyShards.HABIT_CONFIGS -> "Habits of the day"
    DailyShards.LABELS -> "Label"
    DailyShards.HABITS -> "Habit"
    DailyShards.MEAL_LIBRARY -> "Meal library"
    DailyShards.EXERCISE_LIBRARY -> "Exercise library"
    DailyShards.ACTIVITY_LIBRARY -> "Activity library"
    DailyShards.PRESETS -> "Preset"
    DailyShards.SETTINGS -> "Settings"
    else -> collection
}

private fun kindLabel(kind: ConflictKind, otherName: String): String = when (kind) {
    ConflictKind.EDIT_EDIT -> "Edited on both devices"
    ConflictKind.ADD_ADD -> "Added on both devices with different values"
    ConflictKind.DELETE_EDIT -> "Deleted on this phone, edited on $otherName"
    ConflictKind.EDIT_DELETE -> "Edited on this phone, deleted on $otherName"
}

private fun recordTitle(conflict: MergeConflict, view: MergeSheetView): String {
    val record = conflict.local ?: conflict.remote
    fun text(o: JsonObject?, field: String) = (o?.get(field) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    if (conflict.collection == BrainMerge.LEARNING_TILES) {
        val node = view.analysis.local[BrainMerge.NODES]?.get(conflict.key)
            ?: view.analysis.remote[BrainMerge.NODES]?.get(conflict.key)
        return text(node, "name") ?: conflict.key
    }
    if (conflict.collection == BrainMerge.META) {
        return if (conflict.key == "settings") "Motion settings" else "Supermarket aisles"
    }
    val title = listOf("title", "name", "fileName", "exerciseName", "displayName")
        .firstNotNullOfOrNull { text(record, it)?.ifBlank { null } } ?: conflict.key
    val date = text(record, "date")
    return if (date != null && date !in title) "$title ($date)" else title
}

private fun summaryFields(record: JsonObject): List<String> =
    listOf("title", "name", "fileName", "body", "notes", "status").filter { it in record }.take(3)

private fun display(field: String, value: JsonElement?): String = when (value) {
    null, is JsonNull -> "—"
    is JsonArray -> if (value.isEmpty()) "—" else value.joinToString(", ") { display(field, it) }
    is JsonObject -> value.toString().take(200)
    is JsonPrimitive -> {
        val millis = value.longOrNull
        if (!value.isString && millis != null && field.endsWith("At") && millis > 1_000_000_000_000L) {
            formatBackupTime(millis)
        } else {
            value.content.ifBlank { "—" }.take(400)
        }
    }
}
