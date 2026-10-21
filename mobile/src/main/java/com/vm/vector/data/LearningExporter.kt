package com.vm.vector.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A Learn tile as one .txt file, saved through the document picker or shared to another app. */
class LearningExporter(
    private val context: Context,
    private val builder: LearningContextBuilder,
) {
    data class Export(val fileName: String, val text: String)

    suspend fun build(nodeId: String, includePrerequisites: Boolean): Export {
        val learning = builder.build(nodeId, includePrerequisites = includePrerequisites)
        return Export(fileName = fileNameFor(learning.title), text = learning.text + "\n")
    }

    suspend fun save(uri: Uri, export: Export): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file")
            stream.use { it.write(export.text.toByteArray(Charsets.UTF_8)) }
        }
    }

    suspend fun shareIntent(export: Export): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, EXPORT_DIR).apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            val file = File(dir, export.fileName)
            file.writeText(export.text, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, export.fileName.removeSuffix(".txt"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(send, "Share ${export.fileName}")
        }
    }

    companion object {
        private const val EXPORT_DIR = "learn_exports"

        fun fileNameFor(title: String): String {
            val clean = title.trim()
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "")
                .replace(Regex("\\s+"), " ")
                .take(80)
                .ifBlank { "Learn tile" }
            return "$clean.txt"
        }
    }
}
