package com.vm.vector.data

import com.vm.core.models.AudioProgress
import com.vm.core.models.BrainAsset
import com.vm.core.models.BrainAssetSyncState
import com.vm.core.models.BrainFts
import com.vm.core.models.BrainNode
import com.vm.core.models.BrainSection
import com.vm.core.models.BrainTag
import com.vm.core.models.LearningLink
import com.vm.core.models.LearningTile
import com.vm.core.models.LearningTileStatus
import com.vm.core.models.MemoryNote
import com.vm.core.models.MemoryNoteKind
import com.vm.core.models.MemoryNoteKindCount
import com.vm.core.models.MotionDayPoints
import com.vm.core.models.MotionPriority
import com.vm.core.models.MotionRecurrence
import com.vm.core.models.MotionScoreEvent
import com.vm.core.models.MotionScoreSource
import com.vm.core.models.MotionScoring
import com.vm.core.models.MotionTask
import com.vm.core.models.MotionTaskStatus
import com.vm.core.models.SupermarketCycle
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

class BrainRepository(
    private val database: VectorDatabase,
    private val fileStore: BrainFileStore,
) {
    private val nodeDao = database.brainNodeDao()
    private val assetDao = database.brainAssetDao()
    private val noteDao = database.memoryNoteDao()
    private val tileDao = database.learningTileDao()
    private val linkDao = database.learningLinkDao()
    private val progressDao = database.audioProgressDao()
    private val taskDao = database.motionTaskDao()
    private val ftsDao = database.brainFtsDao()
    private val tagDao = database.brainTagDao()
    private val scoreDao = database.motionScoreEventDao()

    // ---- Tag library ----
    /** Memory and Learn tiles each have their own library. */
    fun tagLibrary(section: BrainSection = BrainSection.MEMORY): Flow<List<BrainTag>> = tagDao.getBySection(section)

    /** Returns the library name, reusing an existing tag of [section] that differs only by case. */
    suspend fun ensureTag(name: String, section: BrainSection = BrainSection.MEMORY): String? {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isBlank()) return null
        tagDao.getByNameSync(trimmed, section)?.let { return it.name }
        tagDao.insert(
            BrainTag(
                id = "tag_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
                name = trimmed,
                section = section,
            )
        )
        return trimmed
    }

    suspend fun deleteTag(id: String) = tagDao.deleteById(id)

    // ---- Tiles (memory and Learn) ----
    fun memoryTiles(): Flow<List<BrainNode>> = nodeDao.getBySection(BrainSection.MEMORY)
    fun memoryTile(id: String): Flow<BrainNode?> = nodeDao.getById(id)
    fun memoryKindCounts(): Flow<List<MemoryNoteKindCount>> = noteDao.getKindCountsByNode()

    suspend fun createMemoryTile(name: String, tags: List<String> = emptyList()): BrainNode =
        createTileNode(BrainSection.MEMORY, name, tags)

    private suspend fun createTileNode(
        section: BrainSection,
        name: String,
        tags: List<String>,
        driveFolderId: String? = null,
    ): BrainNode {
        val node = createNode(section, name)
        if (tags.isEmpty() && driveFolderId == null) return node
        val updated = node.copy(tags = tags, driveFolderId = driveFolderId)
        nodeDao.insert(updated)
        indexNode(updated)
        return updated
    }

    suspend fun renameTile(id: String, name: String) {
        val trimmed = name.trim()
        val node = nodeDao.getByIdSync(id) ?: return
        if (trimmed.isBlank() || node.name == trimmed) return
        val updated = node.copy(name = trimmed)
        nodeDao.insert(updated)
        indexNode(updated)
    }

    suspend fun setTileTags(id: String, tags: List<String>) {
        val node = nodeDao.getByIdSync(id) ?: return
        if (node.tags == tags) return
        val updated = node.copy(tags = tags)
        nodeDao.insert(updated)
        indexNode(updated)
    }

    /** Deletes the tile, its sources, and their files (locally and, on next sync, on Drive). */
    suspend fun deleteMemoryTile(id: String) {
        noteDao.getByNodeIdSync(id).forEach { note ->
            note.assetId?.let { markAssetDeleted(it) }
        }
        deleteNode(id)
    }

    // ---- Nodes ----
    fun nodesBySection(section: BrainSection): Flow<List<BrainNode>> = nodeDao.getBySection(section)
    fun children(parentId: String?, section: BrainSection): Flow<List<BrainNode>> =
        nodeDao.getChildren(parentId, section)
    suspend fun getNode(id: String): BrainNode? = nodeDao.getByIdSync(id)

    suspend fun createNode(
        section: BrainSection,
        name: String,
        parentId: String? = null,
        isSystem: Boolean = false
    ): BrainNode {
        val siblings = nodeDao.getChildrenSync(parentId, section)
        val node = BrainNode(
            id = "node_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
            parentId = parentId,
            section = section,
            name = name.trim(),
            sortOrder = siblings.size,
            isSystem = isSystem
        )
        nodeDao.insert(node)
        indexNode(node)
        return node
    }

    suspend fun createNodeFromDrive(
        section: BrainSection,
        name: String,
        parentId: String?,
        driveFolderId: String,
    ): BrainNode {
        val siblings = nodeDao.getChildrenSync(parentId, section)
        val node = BrainNode(
            id = "node_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
            parentId = parentId,
            section = section,
            name = name.trim(),
            sortOrder = siblings.size,
            driveFolderId = driveFolderId,
        )
        nodeDao.insert(node)
        indexNode(node)
        return node
    }

    suspend fun deleteNode(id: String) {
        noteDao.getByNodeIdSync(id).forEach { note ->
            ftsDao.delete(BrainFts.TYPE_NOTE, note.id)
        }
        noteDao.deleteByNodeId(id)
        ftsDao.delete(BrainFts.TYPE_NODE, id)
        nodeDao.deleteById(id)
    }

    // ---- Memory notes ----
    fun inboxNotes(): Flow<List<MemoryNote>> = noteDao.getInbox()
    fun inboxCount(): Flow<Int> = noteDao.getInboxCount()
    fun notesInNode(nodeId: String?): Flow<List<MemoryNote>> = noteDao.getByNodeId(nodeId)
    suspend fun notesInNodeSync(nodeId: String): List<MemoryNote> = noteDao.getByNodeIdSync(nodeId)
    suspend fun getNote(id: String): MemoryNote? = noteDao.getByIdSync(id)
    fun note(id: String): Flow<MemoryNote?> = noteDao.getById(id)

    suspend fun createTextNote(
        title: String,
        body: String,
        nodeId: String? = null,
        tags: List<String> = emptyList(),
        description: String = "",
    ): MemoryNote {
        val note = MemoryNote(
            id = newId("note"),
            nodeId = nodeId,
            kind = MemoryNoteKind.TEXT,
            title = title,
            description = description,
            body = body,
            tags = tags
        )
        noteDao.insert(note)
        indexNote(note)
        onNoteChanged(nodeId)
        return note
    }

    suspend fun createLinkNote(title: String, url: String, nodeId: String? = null): MemoryNote {
        val note = MemoryNote(
            id = newId("note"),
            nodeId = nodeId,
            kind = MemoryNoteKind.LINK,
            title = title.ifBlank { url },
            url = url
        )
        noteDao.insert(note)
        indexNote(note)
        onNoteChanged(nodeId)
        return note
    }

    suspend fun createAudioNoteFromRecording(
        relativePath: String,
        title: String = "Voice note",
        nodeId: String? = null,
    ): MemoryNote {
        val asset = fileStore.registerExistingLocalFile(
            relativePath,
            com.vm.vector.audio.BrainAudioRecorder.MIME_M4A,
        ).getOrThrow()
        return createAssetNote(MemoryNoteKind.AUDIO, title, asset, nodeId = nodeId)
    }

    suspend fun createAssetNote(
        kind: MemoryNoteKind,
        title: String,
        asset: BrainAsset,
        nodeId: String? = null
    ): MemoryNote {
        val note = MemoryNote(
            id = newId("note"),
            nodeId = nodeId,
            kind = kind,
            title = title.ifBlank { asset.fileName },
            assetId = asset.id
        )
        noteDao.insert(note)
        indexNote(note)
        onNoteChanged(nodeId)
        return note
    }

    /**
     * Copies content URIs into a tile folder, or the Inbox when [nodeId] is null. Memory tiles use
     * their name as folder; Learn tiles use their id (matched again by [BrainDrivePullSync]).
     * Returns (imported, anyVideo) so callers can enqueue a Wi-Fi-only sync for video.
     */
    suspend fun importUrisAsNotes(uris: List<android.net.Uri>, nodeId: String?): Pair<Int, Boolean> {
        val node = nodeId?.let { nodeDao.getByIdSync(it) }
        val section = node?.section ?: BrainSection.MEMORY
        val folder = nodeId?.let { if (section == BrainSection.MODEL) it else node?.name ?: it }
        var imported = 0
        var anyVideo = false
        for (uri in uris) {
            val mime = fileStore.mimeType(uri)
            val name = fileStore.displayName(uri) ?: "import_${System.currentTimeMillis()}"
            val wifiOnly = fileStore.isVideoMime(mime)
            val relative = if (folder == null) {
                fileStore.inboxRelativePath(name)
            } else {
                fileStore.relativePath(section, folder, name)
            }
            val asset = fileStore.importFromUri(uri, relative, mime, name, wifiOnly).getOrNull() ?: continue
            createAssetNote(memoryNoteKindForMime(mime), name, asset, nodeId)
            imported++
            anyVideo = anyVideo || wifiOnly
        }
        return imported to anyVideo
    }

    suspend fun saveNote(note: MemoryNote) {
        val saved = note.copy(updatedAt = System.currentTimeMillis())
        noteDao.insert(saved)
        indexNote(saved)
        onNoteChanged(saved.nodeId)
    }

    suspend fun moveNote(noteId: String, nodeId: String?) {
        noteDao.moveToNode(noteId, nodeId, System.currentTimeMillis())
        noteDao.getByIdSync(noteId)?.let { indexNote(it) }
        onNoteChanged(nodeId)
    }

    suspend fun deleteNote(id: String) {
        val note = noteDao.getByIdSync(id) ?: return
        note.assetId?.let { markAssetDeleted(it) }
        ftsDao.delete(BrainFts.TYPE_NOTE, id)
        noteDao.deleteById(id)
    }

    // ---- Learn tiles ----
    fun learnNodes(): Flow<List<BrainNode>> = nodeDao.getBySection(BrainSection.MODEL)
    suspend fun learnNodesSync(): List<BrainNode> = nodeDao.getBySectionSync(BrainSection.MODEL)
    fun learnTiles(): Flow<List<LearningTile>> = tileDao.getAll()
    fun learnTile(id: String): Flow<LearningTile?> = tileDao.getById(id)
    suspend fun getLearnTile(id: String): LearningTile? = tileDao.getByIdSync(id)
    fun learningLinks(): Flow<List<LearningLink>> = linkDao.getAll()
    suspend fun learningLinksSync(): List<LearningLink> = linkDao.getAllSync()
    fun countActiveTiles(): Flow<Int> =
        tileDao.countByStatuses(listOf(LearningTileStatus.IN_PROGRESS, LearningTileStatus.TO_REVIEW))
    fun countDueReviews(): Flow<Int> = tileDao.countByStatuses(listOf(LearningTileStatus.TO_REVIEW))

    suspend fun createLearnTile(
        name: String,
        tags: List<String> = emptyList(),
        driveFolderId: String? = null,
    ): BrainNode {
        val node = createTileNode(BrainSection.MODEL, name, tags, driveFolderId)
        tileDao.insert(LearningTile(id = node.id))
        return node
    }

    /** Learn tile for a Drive folder under Model: linked by Drive id, else by tile id (the local folder name). */
    suspend fun learnTileForDriveFolder(driveFolderId: String, name: String): BrainNode {
        nodeDao.getByDriveFolderIdSync(driveFolderId)?.let { return it }
        val byId = nodeDao.getByIdSync(name)?.takeIf { it.section == BrainSection.MODEL }
        if (byId != null) {
            nodeDao.updateDriveFolderId(byId.id, driveFolderId)
            return byId.copy(driveFolderId = driveFolderId)
        }
        return createLearnTile(name, driveFolderId = driveFolderId)
    }

    suspend fun deleteLearnTile(id: String) {
        linkDao.deleteForNode(id)
        tileDao.deleteById(id)
        deleteMemoryTile(id)
    }

    /** Manual status change from the tile screen or a card long-press. */
    suspend fun setLearnStatus(id: String, status: LearningTileStatus, settings: LearningSettings) {
        val tile = tileDao.getByIdSync(id) ?: LearningTile(id = id)
        if (tile.status == status) return
        val now = System.currentTimeMillis()
        val updated = when (status) {
            LearningTileStatus.LEARNED -> tile.copy(
                learnedAt = now,
                reviewStep = 0,
                nextReviewAt = settings.intervalDays(0)?.let { now + TimeUnit.DAYS.toMillis(it.toLong()) },
            )
            LearningTileStatus.IN_PROGRESS -> tile.copy(lastActivityAt = now, reviewStep = 0, nextReviewAt = null)
            LearningTileStatus.BACKLOG, LearningTileStatus.PRIORITY -> tile.copy(reviewStep = 0, nextReviewAt = null)
            LearningTileStatus.ON_HOLD, LearningTileStatus.TO_REVIEW -> tile
        }
        tileDao.insert(updated.copy(status = status, updatedAt = now))
    }

    /**
     * Review outcome: [solid] climbs one step of the ladder (mastered after the last one, no more
     * reviews); otherwise the tile goes back to In Progress and the ladder restarts.
     */
    suspend fun completeReview(id: String, solid: Boolean, score: Float?, settings: LearningSettings) {
        val tile = tileDao.getByIdSync(id) ?: LearningTile(id = id)
        val now = System.currentTimeMillis()
        val updated = if (solid) {
            val step = tile.reviewStep + 1
            tile.copy(
                status = LearningTileStatus.LEARNED,
                reviewStep = step,
                nextReviewAt = settings.intervalDays(step)?.let { now + TimeUnit.DAYS.toMillis(it.toLong()) },
            )
        } else {
            tile.copy(
                status = LearningTileStatus.IN_PROGRESS,
                reviewStep = 0,
                nextReviewAt = null,
                lastActivityAt = now,
            )
        }
        tileDao.insert(
            updated.copy(
                lastReviewScore = score ?: tile.lastReviewScore,
                lastReviewedAt = now,
                updatedAt = now,
            )
        )
    }

    /** Quiz outside the Review status: records the score and counts as study time. */
    suspend fun saveQuizScore(id: String, score: Float) {
        val tile = tileDao.getByIdSync(id) ?: return
        val now = System.currentTimeMillis()
        tileDao.insert(tile.copy(lastReviewScore = score, lastReviewedAt = now, updatedAt = now))
        touchLearnActivity(id)
    }

    /** Due reviews move to Review; In Progress tiles idle longer than the threshold move to On Hold. */
    suspend fun refreshLearningStatuses(settings: LearningSettings) {
        val now = System.currentTimeMillis()
        for (tile in tileDao.getDueForReview(now)) {
            tileDao.insert(tile.copy(status = LearningTileStatus.TO_REVIEW, updatedAt = now))
        }
        for (tile in tileDao.getStaleInProgress(now - settings.onHoldMillis)) {
            tileDao.insert(tile.copy(status = LearningTileStatus.ON_HOLD, updatedAt = now))
        }
    }

    /** Adds "[fromNodeId] depends on [toNodeId]"; refuses self links and links that would close a cycle. */
    suspend fun addLearningLink(fromNodeId: String, toNodeId: String): Boolean {
        if (fromNodeId == toNodeId) return false
        val dependsOn = linkDao.getAllSync().groupBy({ it.fromNodeId }, { it.toNodeId })
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(listOf(toNodeId))
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == fromNodeId) return false
            if (seen.add(current)) stack.addAll(dependsOn[current].orEmpty())
        }
        linkDao.insert(LearningLink(fromNodeId, toNodeId))
        return true
    }

    suspend fun removeLearningLink(fromNodeId: String, toNodeId: String) = linkDao.delete(fromNodeId, toNodeId)

    /**
     * One-time step after the v36 migration: Learn text sources kept their markdown in `.txt` files,
     * memory notes keep it in `body`.
     */
    suspend fun backfillLearnTextBodies() {
        for (node in nodeDao.getBySectionSync(BrainSection.MODEL)) {
            for (note in noteDao.getByNodeIdSync(node.id)) {
                if (note.kind != MemoryNoteKind.TEXT || note.body.isNotBlank()) continue
                val asset = note.assetId?.let { assetDao.getByIdSync(it) } ?: continue
                val text = fileStore.readTextFile(asset.localRelativePath)?.takeIf { it.isNotBlank() } ?: continue
                noteDao.insert(note.copy(body = text))
            }
        }
        rebuildFtsIndex()
    }

    private suspend fun onNoteChanged(nodeId: String?) {
        if (nodeId != null) touchLearnActivity(nodeId)
    }

    /** Only Learn tiles have a [LearningTile] row, so memory nodes are a no-op here. */
    private suspend fun touchLearnActivity(nodeId: String) {
        val tile = tileDao.getByIdSync(nodeId) ?: return
        val now = System.currentTimeMillis()
        val status = when (tile.status) {
            LearningTileStatus.BACKLOG, LearningTileStatus.PRIORITY, LearningTileStatus.ON_HOLD ->
                LearningTileStatus.IN_PROGRESS
            else -> tile.status
        }
        tileDao.insert(tile.copy(status = status, lastActivityAt = now, updatedAt = now))
    }

    // ---- Audio progress ----
    fun audioProgress(assetId: String): Flow<AudioProgress?> = progressDao.getByAssetId(assetId)
    suspend fun getAudioProgress(assetId: String): AudioProgress? = progressDao.getByAssetIdSync(assetId)
    suspend fun saveAudioProgress(progress: AudioProgress) = progressDao.insert(progress)

    // ---- Motion ----
    fun openTasks(): Flow<List<MotionTask>> = taskDao.getOpen()
    fun openTasksByNode(nodeId: String?): Flow<List<MotionTask>> = taskDao.getOpenByNode(nodeId)
    suspend fun getTask(id: String): MotionTask? = taskDao.getByIdSync(id)
    fun task(id: String): Flow<MotionTask?> = taskDao.getById(id)
    fun countOverdue(now: Long = System.currentTimeMillis()): Flow<Int> = taskDao.countOverdue(now)
    fun countDueToday(): Flow<Int> {
        val (start, end) = todayBounds()
        return taskDao.countDueBetween(start, end)
    }

    /** Motion list tiles (MOTION nodes). The Supermarket list is a JSON file shown as a fixed tile. */
    fun motionLists(): Flow<List<BrainNode>> = nodeDao.getBySection(BrainSection.MOTION)

    /** Open items of a list plus the ones checked today (so they can be unchecked). */
    fun listItems(nodeId: String): Flow<List<MotionTask>> = taskDao.getForList(nodeId, todayBounds().first)

    fun inboxTodos(): Flow<List<MotionTask>> = taskDao.getInbox()
    fun inboxTodoCount(): Flow<Int> = taskDao.countInbox()

    suspend fun createMotionList(name: String): BrainNode = createNode(BrainSection.MOTION, name)

    /** Deletes the list and its items; points already earned stay in the ledger. */
    suspend fun deleteMotionList(id: String) {
        taskDao.idsByNode(id).forEach { ftsDao.delete(BrainFts.TYPE_TASK, it) }
        taskDao.deleteByNodeId(id)
        deleteNode(id)
    }

    suspend fun mergeTodos(ids: List<String>, nodeId: String) {
        if (ids.isEmpty()) return
        taskDao.moveToNode(ids, nodeId, System.currentTimeMillis())
    }

    suspend fun createTask(
        title: String,
        notes: String = "",
        priority: MotionPriority = MotionPriority.NONE,
        dueAt: Long? = null,
        leadMinutes: Int = 0,
        recurrence: MotionRecurrence = MotionRecurrence.NONE,
        nodeId: String? = null,
        linkedTileId: String? = null,
        points: Int? = null,
        target: Double? = null,
        unit: String? = null,
    ): MotionTask {
        val base = MotionScoring.basePoints(priority)
        val task = MotionTask(
            id = newId("task"),
            nodeId = nodeId,
            title = title.trim(),
            notes = notes.trim(),
            priority = priority,
            dueAt = dueAt,
            leadMinutes = leadMinutes,
            recurrence = recurrence,
            linkedTileId = linkedTileId,
            points = points ?: base,
            pointsEdited = points != null && points != base,
            firstDueAt = dueAt,
            target = target?.takeIf { it > 0.0 },
            unit = unit?.trim()?.takeIf { it.isNotEmpty() },
        )
        taskDao.insert(task)
        indexTask(task)
        return task
    }

    suspend fun createTask(draft: MotionItemDraft, nodeId: String?): MotionTask = createTask(
        title = draft.title,
        notes = draft.notes,
        priority = draft.priority,
        dueAt = draft.dueAt,
        recurrence = draft.recurrence,
        nodeId = nodeId,
        points = draft.points,
        target = draft.target,
        unit = draft.unit,
    )

    /** Saves an edited item; the first deadline is remembered so the early bonus never grows. */
    suspend fun saveTask(task: MotionTask) {
        val previous = taskDao.getByIdSync(task.id)
        val saved = task.copy(
            firstDueAt = previous?.firstDueAt ?: task.firstDueAt ?: task.dueAt,
            points = if (task.pointsEdited) task.points else MotionScoring.basePoints(task.priority),
            target = task.target?.takeIf { it > 0.0 },
            updatedAt = System.currentTimeMillis(),
        )
        taskDao.insert(saved)
        indexTask(saved)
        val target = saved.target
        if (target != null && saved.status == MotionTaskStatus.OPEN && saved.progress >= target) checkTask(saved.id)
    }

    /** Watch and legacy entry point. */
    suspend fun completeTask(id: String) {
        checkTask(id)
    }

    /**
     * Freezes the item's points into the ledger and spawns the next recurring occurrence.
     * Returns the points earned (null when the item doesn't exist).
     */
    suspend fun checkTask(id: String, now: Long = System.currentTimeMillis()): Int? {
        val task = taskDao.getByIdSync(id) ?: return null
        if (task.status == MotionTaskStatus.DONE) return task.earnedPoints
        val points = MotionScoring.pointsAt(task, now)
        val dueAt = task.dueAt
        val spawned = if (task.recurrence != MotionRecurrence.NONE && dueAt != null) {
            val nextDue = nextOccurrence(dueAt, task.recurrence)
            val next = task.copy(
                id = newId("task"),
                dueAt = nextDue,
                firstDueAt = nextDue,
                progress = 0.0,
                status = MotionTaskStatus.OPEN,
                completedAt = null,
                earnedPoints = null,
                spawnedTaskId = null,
                createdAt = now,
                updatedAt = now,
            )
            taskDao.insert(next)
            indexTask(next)
            next
        } else {
            null
        }
        val done = task.copy(
            status = MotionTaskStatus.DONE,
            completedAt = now,
            earnedPoints = points,
            spawnedTaskId = spawned?.id,
            progress = task.target?.let { maxOf(it, task.progress) } ?: task.progress,
            updatedAt = now,
        )
        taskDao.insert(done)
        indexTask(done)
        scoreDao.insert(
            MotionScoreEvent(
                id = MotionScoreEvent.taskEventId(id),
                dateKey = MotionScoring.dateKey(now),
                sourceType = MotionScoreSource.TASK,
                sourceId = id,
                title = task.title,
                points = points,
                createdAt = now,
            )
        )
        return points
    }

    /**
     * Reopens the item, removes its points and the occurrence its check spawned (if still open).
     * [progress] is the new value for numeric items; null resets it to 0.
     */
    suspend fun uncheckTask(id: String, progress: Double? = null) {
        val task = taskDao.getByIdSync(id) ?: return
        if (task.status != MotionTaskStatus.DONE) return
        task.spawnedTaskId?.let { spawnedId ->
            val spawned = taskDao.getByIdSync(spawnedId)
            if (spawned?.status == MotionTaskStatus.OPEN) deleteTask(spawnedId)
        }
        scoreDao.deleteById(MotionScoreEvent.taskEventId(id))
        val reopened = task.copy(
            status = MotionTaskStatus.OPEN,
            completedAt = null,
            earnedPoints = null,
            spawnedTaskId = null,
            progress = if (task.target != null) progress ?: 0.0 else task.progress,
            updatedAt = System.currentTimeMillis(),
        )
        taskDao.insert(reopened)
        indexTask(reopened)
    }

    /** Numeric items check themselves at the target and uncheck when progress drops below it. */
    suspend fun setProgress(id: String, progress: Double) {
        val task = taskDao.getByIdSync(id) ?: return
        val target = task.target ?: return
        val value = progress.coerceAtLeast(0.0)
        when {
            task.status == MotionTaskStatus.DONE && value < target -> uncheckTask(id, value)
            task.status == MotionTaskStatus.OPEN && value >= target -> {
                taskDao.insert(task.copy(progress = value, updatedAt = System.currentTimeMillis()))
                checkTask(id)
            }
            else -> taskDao.insert(task.copy(progress = value, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun snoozeTask(id: String, untilMillis: Long) {
        val task = taskDao.getByIdSync(id) ?: return
        val updated = task.copy(
            dueAt = untilMillis,
            firstDueAt = task.firstDueAt ?: untilMillis,
            updatedAt = System.currentTimeMillis(),
        )
        taskDao.insert(updated)
        indexTask(updated)
    }

    suspend fun deleteTask(id: String) {
        ftsDao.delete(BrainFts.TYPE_TASK, id)
        scoreDao.deleteById(MotionScoreEvent.taskEventId(id))
        taskDao.deleteById(id)
    }

    // ---- Motion score ----
    /** Points per local day since [from] (inclusive). */
    fun dailyPoints(from: LocalDate): Flow<List<MotionDayPoints>> = scoreDao.dailyPointsSince(from.toString())

    fun lastSupermarketTrip(): Flow<MotionScoreEvent?> = scoreDao.lastSupermarketTrip()

    suspend fun supermarketCycle(settings: MotionSettings, today: LocalDate = LocalDate.now()): SupermarketCycle {
        val lastDue = scoreDao.lastSupermarketTripSync()?.sourceId?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return MotionScoring.supermarketCycle(lastDue, today, settings.supermarketDay)
    }

    /** Logs a trip for the open cycle; returns its points, or null when this week's trip is already logged. */
    suspend fun recordSupermarketTrip(settings: MotionSettings, now: Long = System.currentTimeMillis()): Int? {
        val today = MotionScoring.localDate(now)
        val cycle = supermarketCycle(settings, today)
        if (!cycle.open) return null
        val points = MotionScoring.supermarketPoints(cycle.dueDate, today)
        scoreDao.insert(
            MotionScoreEvent(
                id = "supermarket_${cycle.dueDate}",
                dateKey = today.toString(),
                sourceType = MotionScoreSource.SUPERMARKET,
                sourceId = cycle.dueDate.toString(),
                title = "Supermarket trip",
                points = points,
                createdAt = now,
            )
        )
        return points
    }

    suspend fun upcomingOpen(limit: Int = 3): List<MotionTask> {
        val open = taskDao.getOpenSync().filter { it.nodeId != null }
        val now = System.currentTimeMillis()
        return open
            .sortedWith(
                compareBy<MotionTask> { it.dueAt == null }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.priority.ordinal }
            )
            .filter { task ->
                val due = task.dueAt
                due == null || due >= now - TimeUnit.DAYS.toMillis(1) || due < now
            }
            .take(limit)
    }

    // ---- Search ----
    suspend fun searchAll(query: String): BrainSearchResult {
        val q = query.trim()
        if (q.isBlank()) return BrainSearchResult()
        if (ftsDao.count() == 0 && hasBrainContentForFts()) {
            rebuildFtsIndex()
        }
        val match = toFtsMatchQuery(q)
        val hits = runCatching { ftsDao.search(match) }.getOrDefault(emptyList())
        if (hits.isEmpty()) {
            return BrainSearchResult(
                notes = noteDao.search(q),
                tasks = taskDao.search(q),
                nodes = nodeDao.searchByName(q)
            )
        }
        val notes = mutableListOf<MemoryNote>()
        val tasks = mutableListOf<MotionTask>()
        val nodes = mutableListOf<BrainNode>()
        val seenNotes = mutableSetOf<String>()
        val seenTasks = mutableSetOf<String>()
        val seenNodes = mutableSetOf<String>()
        for (hit in hits) {
            when (hit.entityType) {
                BrainFts.TYPE_NOTE -> if (seenNotes.add(hit.entityId)) {
                    noteDao.getByIdSync(hit.entityId)?.let { notes.add(it) }
                }
                BrainFts.TYPE_TASK -> if (seenTasks.add(hit.entityId)) {
                    taskDao.getByIdSync(hit.entityId)?.let { tasks.add(it) }
                }
                BrainFts.TYPE_NODE -> if (seenNodes.add(hit.entityId)) {
                    nodeDao.getByIdSync(hit.entityId)?.let { nodes.add(it) }
                }
            }
        }
        return BrainSearchResult(
            notes = notes,
            tasks = tasks,
            nodes = nodes,
        )
    }

    suspend fun rebuildFtsIndex() {
        ftsDao.clear()
        noteDao.getAllSync().forEach { indexNote(it) }
        taskDao.getAllSync().forEach { indexTask(it) }
        nodeDao.getAllSync().forEach { indexNode(it) }
    }

    suspend fun updateNoteBody(noteId: String, body: String) {
        val note = noteDao.getByIdSync(noteId) ?: return
        if (note.body == body) return
        val updated = note.copy(body = body, updatedAt = System.currentTimeMillis())
        noteDao.insert(updated)
        indexNote(updated)
        onNoteChanged(updated.nodeId)
    }

    suspend fun applyTranscription(noteId: String, title: String, transcript: String) {
        val note = noteDao.getByIdSync(noteId) ?: return
        val fileName = note.assetId?.let { assetDao.getByIdSync(it)?.fileName }.orEmpty()
        val keepTitle = !isGeneratedAudioTitle(note.title, fileName)
        val updated = note.copy(
            title = if (keepTitle) note.title else title.ifBlank { note.title },
            body = transcript,
            updatedAt = System.currentTimeMillis(),
        )
        noteDao.insert(updated)
        indexNote(updated)
        onNoteChanged(updated.nodeId)
    }

    suspend fun notesForAsset(assetId: String): List<MemoryNote> = noteDao.getByAssetIdSync(assetId)

    suspend fun renameRecording(assetId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        val now = System.currentTimeMillis()
        val notes = noteDao.getByAssetIdSync(assetId)
        notes.forEach { note ->
            if (note.title == trimmed) return@forEach
            val updated = note.copy(title = trimmed, updatedAt = now)
            noteDao.insert(updated)
            indexNote(updated)
        }
        if (notes.isEmpty()) {
            val asset = assetDao.getByIdSync(assetId) ?: return
            if (asset.fileName != trimmed) {
                assetDao.insert(asset.copy(fileName = trimmed, updatedAt = now))
            }
        }
    }

    suspend fun updateRecordingDescription(assetId: String, description: String) {
        val asset = assetDao.getByIdSync(assetId) ?: return
        val trimmed = description.trim()
        if (asset.description == trimmed) return
        assetDao.insert(asset.copy(description = trimmed, updatedAt = System.currentTimeMillis()))
        noteDao.getByAssetIdSync(assetId).forEach { indexNote(it) }
    }

    private fun isGeneratedAudioTitle(title: String, fileName: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.isBlank() || trimmed == fileName) return true
        return trimmed.startsWith("Voice note ") || trimmed.startsWith("Watch voice ")
    }

    // ---- Assets ----
    suspend fun getAsset(id: String): BrainAsset? = assetDao.getByIdSync(id)
    suspend fun getAssetByDriveFileId(driveFileId: String): BrainAsset? =
        assetDao.getByDriveFileIdSync(driveFileId)

    suspend fun getNodeByDriveFolderId(driveFolderId: String): BrainNode? =
        nodeDao.getByDriveFolderIdSync(driveFolderId)

    /** Memory tile for a Drive folder: linked by id, else a same-named tile created on the phone. */
    suspend fun memoryTileForDriveFolder(driveFolderId: String, name: String): BrainNode {
        nodeDao.getByDriveFolderIdSync(driveFolderId)?.let { return it }
        val unlinked = nodeDao.getBySectionSync(BrainSection.MEMORY).firstOrNull {
            it.driveFolderId == null && it.name.equals(name.trim(), ignoreCase = true)
        }
        if (unlinked != null) {
            nodeDao.updateDriveFolderId(unlinked.id, driveFolderId)
            return unlinked.copy(driveFolderId = driveFolderId)
        }
        return createNodeFromDrive(BrainSection.MEMORY, name, null, driveFolderId)
    }
    fun fileStore(): BrainFileStore = fileStore

    suspend fun markAssetDeleted(assetId: String) {
        val asset = assetDao.getByIdSync(assetId) ?: return
        if (asset.driveFileId.isNullOrBlank()) {
            fileStore.deleteLocal(asset.localRelativePath)
            assetDao.deleteById(assetId)
        } else {
            assetDao.updateSyncState(assetId, BrainAssetSyncState.DELETE_PENDING, System.currentTimeMillis())
        }
    }

    private fun nextOccurrence(from: Long, recurrence: MotionRecurrence): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        when (recurrence) {
            MotionRecurrence.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            MotionRecurrence.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            MotionRecurrence.MONTHLY -> cal.add(Calendar.MONTH, 1)
            MotionRecurrence.NONE -> {}
        }
        return cal.timeInMillis
    }

    private fun todayBounds(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return start to (start + TimeUnit.DAYS.toMillis(1))
    }

    private fun newId(prefix: String) =
        "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

    private suspend fun hasBrainContentForFts(): Boolean =
        noteDao.getAllSync().isNotEmpty() ||
            taskDao.getAllSync().isNotEmpty() ||
            nodeDao.getAllSync().isNotEmpty()

    private suspend fun indexNote(note: MemoryNote) {
        val assetDescription = note.assetId?.let { assetDao.getByIdSync(it)?.description }.orEmpty()
        ftsDao.delete(BrainFts.TYPE_NOTE, note.id)
        ftsDao.insert(
            BrainFts(
                BrainFts.TYPE_NOTE,
                note.id,
                listOf(
                    note.title,
                    note.description,
                    note.body,
                    assetDescription,
                    note.tags.joinToString(" "),
                    note.url.orEmpty(),
                    note.kind.name,
                ).filter { it.isNotBlank() }.joinToString(" "),
            )
        )
    }

    private suspend fun indexTask(task: MotionTask) {
        ftsDao.delete(BrainFts.TYPE_TASK, task.id)
        ftsDao.insert(
            BrainFts(
                BrainFts.TYPE_TASK,
                task.id,
                listOf(task.title, task.notes).filter { it.isNotBlank() }.joinToString(" "),
            )
        )
    }

    private suspend fun indexNode(node: BrainNode) {
        ftsDao.delete(BrainFts.TYPE_NODE, node.id)
        ftsDao.insert(
            BrainFts(
                BrainFts.TYPE_NODE,
                node.id,
                listOf(node.name, node.section.name, node.tags.joinToString(" "))
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
            )
        )
    }

    private fun toFtsMatchQuery(raw: String): String {
        val tokens = raw.split(Regex("\\s+"))
            .map { it.replace("\"", "").trim() }
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return "\"${raw.replace("\"", "")}*\""
        return tokens.joinToString(" AND ") { "$it*" }
    }
}

fun memoryNoteKindForMime(mimeType: String): MemoryNoteKind = when {
    mimeType.startsWith("audio/", ignoreCase = true) -> MemoryNoteKind.AUDIO
    mimeType.startsWith("image/", ignoreCase = true) -> MemoryNoteKind.IMAGE
    mimeType.startsWith("video/", ignoreCase = true) -> MemoryNoteKind.VIDEO
    mimeType.equals("application/pdf", ignoreCase = true) -> MemoryNoteKind.PDF
    mimeType.startsWith("text/", ignoreCase = true) -> MemoryNoteKind.TEXT
    else -> MemoryNoteKind.FILE
}

data class BrainSearchResult(
    val notes: List<MemoryNote> = emptyList(),
    val tasks: List<MotionTask> = emptyList(),
    val nodes: List<BrainNode> = emptyList()
)
