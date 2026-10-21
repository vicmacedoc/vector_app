package com.vm.vector.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vm.vector.VectorApplication
import com.vm.vector.data.DailySync
import com.vm.vector.data.backup.BackupNotifier
import com.vm.vector.data.sync.DailyShards
import com.vm.vector.data.sync.ShardMergeView
import com.vm.vector.data.sync.ShardSync
import com.vm.vector.data.sync.ShardSyncFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Merge sheet for daily shards (Calendar month or settings & libraries) changed on both devices; one shard at a time. */
@Composable
fun DailyMergeHost() {
    val context = LocalContext.current
    val app = context.applicationContext as VectorApplication
    val files = remember { ShardSyncFiles(app) }
    val sync = remember { ShardSync(app, app.database) }
    val pending by ShardSyncFiles.pending.collectAsState()
    var dismissed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var view by remember { mutableStateOf<ShardMergeView?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { files.refreshPendingFlag() } }
    LaunchedEffect(pending, reloadKey) {
        dismissed = dismissed intersect pending.toSet()
        val shard = pending.firstOrNull { it !in dismissed }
        view = shard?.let { withContext(Dispatchers.IO) { sync.loadPending(it) } }
    }

    val current = view ?: return
    if (current.shard in dismissed) return

    MergeSheet(
        view = MergeSheetView(
            "${DailyShards.label(current.shard)} changed on two devices",
            current.analysis,
            current.remoteDevice,
            current.remoteWrittenAt,
        ),
        onApply = { choice, choices ->
            val report = withContext(Dispatchers.IO) { sync.resolvePending(current.shard, choice, choices) }
            when {
                !report.ok -> MergeApplyResult.Failed(report.message ?: "Merge failed")
                current.shard in report.conflicts -> {
                    reloadKey++
                    MergeApplyResult.Changed
                }
                else -> {
                    if (files.pendingShards().isEmpty()) BackupNotifier.cancelDailyMerge(app)
                    DailySync.notifyImported()
                    view = null
                    MergeApplyResult.Done
                }
            }
        },
        onLater = { dismissed = dismissed + current.shard },
    )
}
