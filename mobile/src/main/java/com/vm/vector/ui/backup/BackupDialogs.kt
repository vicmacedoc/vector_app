package com.vm.vector.ui.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.vector.AppRestarter
import com.vm.vector.VectorApplication
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.backup.BackupConflict
import com.vm.vector.data.backup.BackupConflictReason
import com.vm.vector.data.backup.BackupOutcome
import com.vm.vector.data.backup.BackupSnapshotInfo
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBackupTime(millis: Long): String =
    SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatSize(bytes: Long?): String = when {
    bytes == null -> ""
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "${(bytes / 1024).coerceAtLeast(1)} KB"
}

@Composable
fun RestorePickerDialog(
    isLoading: Boolean,
    snapshots: List<BackupSnapshotInfo>,
    onPick: (BackupSnapshotInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from Drive", color = PureBlack) },
        text = {
            when {
                isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(28.dp))
                }
                snapshots.isEmpty() -> Text("No backups found in Drive.", color = PureBlack)
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(snapshots, key = { it.driveFileId }) { snapshot ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(snapshot) }
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                if (snapshot.isLatest) "Latest backup" else formatBackupTime(snapshot.createdAt),
                                color = PureBlack,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                listOfNotNull(
                                    if (snapshot.isLatest) formatBackupTime(snapshot.createdAt) else "Daily snapshot",
                                    formatSize(snapshot.sizeBytes).ifBlank { null },
                                ).joinToString(" · "),
                                color = SlateGray,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = PureBlack)) {
                Text("Close")
            }
        },
        containerColor = PureWhite,
    )
}

/**
 * Shown anywhere in the app while an automatic backup is held back by the overwrite guard.
 * Offers: restore Drive's copy, overwrite it with this phone's data, or archive it and then overwrite.
 */
@Composable
fun BackupConflictHost() {
    val context = LocalContext.current
    val app = context.applicationContext as VectorApplication
    val prefs = remember { PreferenceManager(app) }
    val raw by prefs.backupConflict.collectAsState(initial = null)
    val conflict = remember(raw) {
        raw?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<BackupConflict>(it) }.getOrNull() }
    } ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> String?) {
        busy = true
        error = null
        scope.launch {
            error = action()
            busy = false
        }
    }

    val remoteWhen = conflict.remote?.let { "${formatBackupTime(it.createdAt)} by ${it.deviceName}" } ?: "an older app version"
    val body = when (conflict.reason) {
        BackupConflictReason.OTHER_INSTALL_NEWER ->
            "Drive's latest backup was written on $remoteWhen, after this phone last backed up. " +
                "Backing up now would replace it."
        BackupConflictReason.LOCAL_MUCH_SMALLER ->
            "This phone has much less data (${conflict.local.totalRows} rows) than Drive's latest backup " +
                "(${conflict.remote?.totalRows?.toString() ?: formatSize(conflict.remoteSizeBytes)}, $remoteWhen). " +
                "This usually happens after a reinstall."
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Backup paused", color = PureBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, color = PureBlack)
                Text(
                    "Restore: replace this phone's data with Drive's copy.\n" +
                        "Overwrite: replace Drive's copy with this phone's data.\n" +
                        "Keep both: archive Drive's copy under backups/db, then back up this phone.",
                    color = SlateGray,
                )
                if (busy) CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(24.dp))
                error?.let { Text(it, color = DeleteRed) }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        run {
                            val result = app.databaseBackupManager.restore()
                            if (result.isSuccess) {
                                AppRestarter.restart(app)
                                null
                            } else {
                                "Restore failed: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = NavyDeep),
                ) { Text("Restore from Drive") }
                TextButton(
                    enabled = !busy,
                    onClick = { run { outcomeMessage(app.databaseBackupManager.keepBothThenOverwrite()) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = NavyDeep),
                ) { Text("Keep both") }
                TextButton(
                    enabled = !busy,
                    onClick = { run { outcomeMessage(app.databaseBackupManager.backupLatest(force = true)) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = DeleteRed),
                ) { Text("Overwrite Drive") }
            }
        },
        containerColor = PureWhite,
    )
}

private fun outcomeMessage(outcome: BackupOutcome): String? = when (outcome) {
    is BackupOutcome.Success -> null
    is BackupOutcome.Conflict -> "Drive changed again; try once more."
    is BackupOutcome.Failure -> outcome.message
}
