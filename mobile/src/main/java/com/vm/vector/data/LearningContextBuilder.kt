package com.vm.vector.data

import com.vm.core.models.BrainNode
import com.vm.core.models.LearningTileStatus
import com.vm.core.models.MemoryNote
import com.vm.core.models.MemoryNoteKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-text view of a Learn tile, shared by the .txt export and the Gemini prompts. */
data class LearningContext(
    val nodeId: String,
    val title: String,
    val text: String,
    /** PDFs small enough to attach to a Gemini request; empty unless asked for. */
    val pdfs: List<File> = emptyList(),
)

class LearningContextBuilder(private val repository: BrainRepository) {

    suspend fun build(
        nodeId: String,
        includePrerequisites: Boolean = false,
        includePdfs: Boolean = false,
    ): LearningContext {
        val node = repository.getNode(nodeId) ?: error("Tile not found")
        val nodesById = repository.learnNodesSync().associateBy { it.id }
        val links = repository.learningLinksSync()
        val prerequisites = links.filter { it.fromNodeId == nodeId }.mapNotNull { nodesById[it.toNodeId] }
        val dependents = links.filter { it.toNodeId == nodeId }.mapNotNull { nodesById[it.fromNodeId] }
        val pdfs = mutableListOf<File>()

        val text = buildString {
            appendTile(node, prerequisites, dependents, pdfs.takeIf { includePdfs })
            if (includePrerequisites) {
                for (prerequisite in prerequisites) {
                    append("\n\n")
                    append("=".repeat(60)).append('\n')
                    append("PREREQUISITE\n")
                    append("=".repeat(60)).append("\n\n")
                    appendTile(prerequisite, emptyList(), emptyList(), null)
                }
            }
        }
        return LearningContext(nodeId = nodeId, title = node.name, text = text.trim(), pdfs = pdfs)
    }

    /** One line per Learn tile, used as link candidates for Gemini. */
    suspend fun candidateList(excludeNodeId: String): List<BrainNode> =
        repository.learnNodesSync().filter { it.id != excludeNodeId }

    private suspend fun StringBuilder.appendTile(
        node: BrainNode,
        prerequisites: List<BrainNode>,
        dependents: List<BrainNode>,
        pdfSink: MutableList<File>?,
    ) {
        val tile = repository.getLearnTile(node.id)
        append("# ").append(node.name).append('\n')
        tile?.let {
            append("Status: ").append(statusLabel(it.status)).append('\n')
            append("Last activity: ").append(formatDate(it.lastActivityAt)).append('\n')
            it.learnedAt?.let { at -> append("Learned: ").append(formatDate(at)).append('\n') }
            it.nextReviewAt?.let { at -> append("Next review: ").append(formatDate(at)).append('\n') }
        }
        if (node.tags.isNotEmpty()) append("Tags: ").append(node.tags.joinToString(", ")).append('\n')
        append("Created: ").append(formatDate(node.createdAt)).append('\n')
        if (prerequisites.isNotEmpty()) {
            append("Depends on: ").append(prerequisites.joinToString(", ") { it.name }).append('\n')
        }
        if (dependents.isNotEmpty()) {
            append("Used in: ").append(dependents.joinToString(", ") { it.name }).append('\n')
        }

        val notes = repository.notesInNodeSync(node.id)
            .sortedWith(compareByDescending<MemoryNote> { it.pinned }.thenBy { it.createdAt })
        val files = mutableListOf<String>()
        for (note in notes) {
            when (note.kind) {
                MemoryNoteKind.TEXT -> {
                    append("\n## ").append(note.title.ifBlank { "Note" }).append('\n')
                    if (note.description.isNotBlank()) append(note.description.trim()).append("\n\n")
                    if (note.body.isNotBlank()) append(note.body.trim()).append('\n')
                }
                MemoryNoteKind.LINK -> {
                    append("\n## Link: ").append(note.title.ifBlank { note.url.orEmpty() }).append('\n')
                    note.url?.let { append(it).append('\n') }
                    if (note.description.isNotBlank()) append(note.description.trim()).append('\n')
                    if (note.body.isNotBlank()) append(note.body.trim()).append('\n')
                }
                else -> {
                    val asset = note.assetId?.let { repository.getAsset(it) }
                    val name = asset?.fileName?.ifBlank { null } ?: note.title.ifBlank { "File" }
                    files += "${memoryKindName(note.kind)}: $name"
                    val hasText = note.description.isNotBlank() || note.body.isNotBlank()
                    if (hasText) {
                        append("\n## ").append(memoryKindName(note.kind)).append(": ")
                            .append(note.title.ifBlank { name }).append('\n')
                        if (note.description.isNotBlank()) append(note.description.trim()).append('\n')
                        if (note.body.isNotBlank()) append(note.body.trim()).append('\n')
                    }
                    if (pdfSink != null && asset != null &&
                        asset.mimeType.equals("application/pdf", ignoreCase = true) &&
                        asset.sizeBytes in 1..MAX_PDF_BYTES
                    ) {
                        val file = repository.fileStore().absoluteFile(asset.localRelativePath)
                        if (file.exists()) pdfSink += file
                    }
                }
            }
        }
        if (files.isNotEmpty()) {
            append("\n## Files\n")
            files.forEach { append("- ").append(it).append('\n') }
        }
    }

    private fun statusLabel(status: LearningTileStatus): String = when (status) {
        LearningTileStatus.BACKLOG -> "Backlog"
        LearningTileStatus.PRIORITY -> "Backlog (priority)"
        LearningTileStatus.IN_PROGRESS -> "In Progress"
        LearningTileStatus.ON_HOLD -> "On Hold"
        LearningTileStatus.TO_REVIEW -> "Review"
        LearningTileStatus.LEARNED -> "Learned"
    }

    private fun memoryKindName(kind: MemoryNoteKind): String =
        kind.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    companion object {
        const val MAX_PDF_BYTES = 10L * 1024L * 1024L
    }
}
