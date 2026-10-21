package com.vm.vector.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vm.core.models.BrainNode
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.BrainSyncWorker
import com.vm.vector.data.memoryNoteKindForMime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareToBrainActivity : ComponentActivity() {

    private lateinit var repository: BrainRepository
    private lateinit var fileStore: BrainFileStore
    private lateinit var payload: SharePayload

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VectorApplication
        fileStore = BrainFileStore(app, app.database)
        repository = BrainRepository(app.database, fileStore)
        payload = parseShareIntent(intent) ?: run {
            finish()
            return
        }

        renderMainScreen()
    }

    private fun openTilePicker() {
        lifecycleScope.launch {
            val tiles = repository.learnNodes().first().sortedBy { it.name.lowercase() }
            setContent {
                VectorTheme {
                    TilePickerScreen(
                        tiles = tiles,
                        onTile = { tile -> saveToModelAndFinish(tile.id) },
                        onBack = { renderMainScreen() },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun renderMainScreen() {
        setContent {
            VectorTheme {
                ShareToBrainScreen(
                    payload = payload,
                    onSaveMemory = { saveMemoryAndFinish() },
                    onPickModel = { openTilePicker() },
                    onSaveMotion = { saveMotionAndFinish() },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun saveMemoryAndFinish() {
        lifecycleScope.launch {
            runCatching {
                saveToMemory(payload)
                BrainSyncWorker.enqueue(applicationContext)
            }.onSuccess {
                toastAndFinish("Saved to Inbox")
            }.onFailure {
                toastAndFinish("Save failed")
            }
        }
    }

    private fun saveToModelAndFinish(tileId: String) {
        lifecycleScope.launch {
            runCatching {
                saveToModel(payload, tileId)
                BrainSyncWorker.enqueue(applicationContext)
            }.onSuccess {
                toastAndFinish("Added to tile")
            }.onFailure {
                toastAndFinish("Save failed")
            }
        }
    }

    private fun saveMotionAndFinish() {
        lifecycleScope.launch {
            runCatching {
                saveToMotion(payload)
            }.onSuccess {
                toastAndFinish("Task added")
            }.onFailure {
                toastAndFinish("Save failed")
            }
        }
    }

    private fun toastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private suspend fun saveToMemory(payload: SharePayload) {
        if (payload.uris.isEmpty()) {
            val text = payload.text?.trim().orEmpty()
            if (text.isBlank()) return
            val url = extractHttpUrl(text)
            if (url != null) {
                repository.createLinkNote(url, url, nodeId = null)
            } else {
                val title = text.lineSequence().firstOrNull()?.take(120).orEmpty()
                repository.createTextNote(
                    title = title.ifBlank { "Shared note" },
                    body = text,
                    nodeId = null,
                )
            }
            return
        }
        for (item in payload.uris) {
            importAsMemoryNote(item.uri, item.mimeType, item.displayName)
        }
    }

    private suspend fun saveToModel(payload: SharePayload, tileId: String) {
        if (payload.uris.isEmpty()) {
            val text = payload.text?.trim().orEmpty()
            if (text.isBlank()) return
            val url = extractHttpUrl(text)
            if (url != null) {
                repository.createLinkNote(url, url, nodeId = tileId)
            } else {
                val title = text.lineSequence().firstOrNull()?.take(120).orEmpty()
                repository.createTextNote(title = title.ifBlank { "Shared note" }, body = text, nodeId = tileId)
            }
            return
        }
        repository.importUrisAsNotes(payload.uris.map { it.uri }, tileId)
    }

    private suspend fun saveToMotion(payload: SharePayload) {
        val title = when {
            payload.uris.isNotEmpty() -> payload.uris.first().displayName
            !payload.text.isNullOrBlank() -> {
                val text = payload.text.trim()
                extractHttpUrl(text) ?: text.lineSequence().firstOrNull()?.take(200).orEmpty()
            }
            else -> "Shared item"
        }
        val notes = payload.text?.trim().orEmpty()
        repository.createTask(
            title = title.ifBlank { "Shared item" },
            notes = if (notes != title) notes else "",
        )
    }

    private suspend fun importAsMemoryNote(uri: Uri, mimeType: String, displayName: String) {
        val safeName = displayName.ifBlank { "shared_${System.currentTimeMillis()}" }
        val relative = fileStore.inboxRelativePath(safeName)
        val wifiOnly = fileStore.isVideoMime(mimeType)
        val asset = fileStore.importFromUri(uri, relative, mimeType, safeName, wifiOnly).getOrThrow()
        repository.createAssetNote(memoryNoteKindForMime(mimeType), safeName, asset, nodeId = null)
    }

    private fun parseShareIntent(intent: Intent): SharePayload? {
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val uris = mutableListOf<ShareUriItem>()
        when (action) {
            Intent.ACTION_SEND -> {
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) {
                    uris += uriItem(stream, intent.type)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val streams = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                streams?.forEach { uri ->
                    uris += uriItem(uri, intent.type)
                }
            }
        }
        if (text.isNullOrBlank() && uris.isEmpty()) return null
        return SharePayload(text = text, uris = uris)
    }

    private fun uriItem(uri: Uri, fallbackMime: String?): ShareUriItem {
        val mime = contentResolver.getType(uri) ?: fallbackMime ?: "*/*"
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "file"
        return ShareUriItem(uri, mime, name)
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        }

    companion object {
        private val URL_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

        fun extractHttpUrl(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.matches(URL_REGEX)) return trimmed
            return URL_REGEX.find(trimmed)?.value?.trimEnd(',', '.', ')')
        }
    }
}

private data class SharePayload(
    val text: String?,
    val uris: List<ShareUriItem>,
)

private data class ShareUriItem(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareToBrainScreen(
    payload: SharePayload,
    onSaveMemory: () -> Unit,
    onPickModel: () -> Unit,
    onSaveMotion: () -> Unit,
    onCancel: () -> Unit,
) {
    val preview = remember(payload) {
        when {
            payload.uris.isNotEmpty() -> payload.uris.joinToString { it.displayName }
            !payload.text.isNullOrBlank() -> payload.text.take(120)
            else -> "Shared content"
        }
    }

    Scaffold(
        containerColor = PureWhite,
        topBar = {
            TopAppBar(
                title = { Text("Save to Brain", color = PureBlack) },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel", color = NavyDeep) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = PureBlack.copy(alpha = 0.75f),
            )
            DestinationButton("Brain Inbox", onClick = onSaveMemory)
            DestinationButton("Learn tile", onClick = onPickModel)
            DestinationButton("Motion task", onClick = onSaveMotion)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TilePickerScreen(
    tiles: List<BrainNode>,
    onTile: (BrainNode) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        containerColor = PureWhite,
        topBar = {
            TopAppBar(
                title = { Text("Pick learning tile", color = PureBlack) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = NavyDeep) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite),
            )
        },
    ) { padding ->
        if (tiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text("No tiles yet. Create one in Brain → Model.", color = PureBlack)
                TextButton(onClick = onCancel) { Text("Close", color = NavyDeep) }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                items(tiles, key = { it.id }) { tile ->
                    TextButton(
                        onClick = { onTile(tile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tile.name, color = PureBlack, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
    ) {
        Text(label)
    }
}
