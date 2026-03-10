package com.vm.vector.data

import android.content.Context
import com.vm.core.models.VectorList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class ListsManifest(val files: List<DriveFileInfo> = emptyList())

/**
 * Local cache for Lists JSON files. Stores a manifest (id + name per file) and one JSON file per list.
 * Used so the Lists screen can work offline; refresh replaces this cache with Drive contents.
 */
@OptIn(ExperimentalSerializationApi::class)
class ListsLocalCache(private val context: Context) {

    private val cacheDir: File
        get() = File(context.filesDir, LISTS_CACHE_DIR).also { if (!it.exists()) it.mkdirs() }

    private val manifestFile: File
        get() = File(cacheDir, MANIFEST_FILE)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; prettyPrintIndent = "  " }

    /**
     * Removes all cached JSON files and clears the manifest.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Saves the manifest (list of id + name). Overwrites existing.
     */
    suspend fun saveManifest(entries: List<DriveFileInfo>) = withContext(Dispatchers.IO) {
        val raw = json.encodeToString(ListsManifest(entries))
        manifestFile.writeText(raw)
    }

    /**
     * Loads the manifest. Returns empty list if missing or invalid.
     */
    suspend fun loadManifest(): List<DriveFileInfo> = withContext(Dispatchers.IO) {
        if (!manifestFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<ListsManifest>(manifestFile.readText()).files
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "list.json" }

    /**
     * Saves list content for a file (by id). Uses manifest to resolve id -> name for the file.
     */
    suspend fun saveFile(fileId: String, name: String, list: VectorList) = withContext(Dispatchers.IO) {
        val export = list.copy(
            id = "",
            items = list.items.map { it.copy(id = "") }
        )
        val content = json.encodeToString(export)
        val file = File(cacheDir, safeFileName(name))
        file.writeText(content)
    }

    /**
     * Saves raw JSON content for a file (by name). Used when downloading from Drive.
     */
    suspend fun saveFileContent(fileName: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, safeFileName(fileName))
        file.writeText(content)
    }

    /**
     * Loads list content for a file id. Uses manifest to find file name.
     */
    suspend fun loadFileContent(fileId: String): VectorList? = withContext(Dispatchers.IO) {
        val manifest = loadManifest()
        val name = manifest.find { it.id == fileId }?.name ?: return@withContext null
        val file = File(cacheDir, safeFileName(name))
        if (!file.exists()) return@withContext null
        try {
            val raw = file.readText()
            var list = json.decodeFromString<VectorList>(raw)
            val baseTime = System.currentTimeMillis()
            list = list.copy(
                id = list.id.ifBlank { "list_$baseTime" },
                items = list.items.mapIndexed { i, item ->
                    item.copy(id = item.id.ifBlank { "item_${baseTime}_$i" })
                }
            )
            list
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads raw content for a file by name (for writing after download we already have name).
     */
    suspend fun loadRawContent(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, safeFileName(fileName))
        if (file.exists()) file.readText() else null
    }

    /**
     * Removes one file from the cache by name.
     */
    suspend fun removeFile(fileName: String) = withContext(Dispatchers.IO) {
        File(cacheDir, safeFileName(fileName)).delete()
    }

    companion object {
        private const val LISTS_CACHE_DIR = "lists_cache"
        private const val MANIFEST_FILE = "manifest.json"
    }
}
