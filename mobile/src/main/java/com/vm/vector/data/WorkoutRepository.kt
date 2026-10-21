package com.vm.vector.data

import android.content.Context
import kotlinx.coroutines.flow.first
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutSet
import com.vm.core.models.WorkoutDaySession
import com.vm.core.models.WorkoutExercisePlan
import com.vm.core.models.WeeklyWorkoutPlan
import com.vm.core.models.MuscleTargets
import com.vm.core.models.isDropRow
import com.vm.core.models.isInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

/**
 * Workout blueprint loader and local persistence.
 * Blueprints: preset JSON from [PresetStorage]. Execution data: Room workout_sets only. No Drive log.
 */
class WorkoutRepository(
    private val context: Context,
    private val database: VectorDatabase,
    private val preferenceManager: PreferenceManager,
    private val presetStorage: PresetStorage,
) {
    private val dao = database.workoutSetDao()
    private val libraryRepository = LibraryRepository(database)

    companion object {
        /** Persisted when user saves an empty workout so Home Daily Inputs / completion see the day as logged. */
        const val REST_DAY_ACK_EXERCISE_ID: String = "__vector_rest_day_ack__"

        fun isRestDayAcknowledgment(set: WorkoutSet): Boolean =
            set.exerciseId == REST_DAY_ACK_EXERCISE_ID

        private val ADDED_EXERCISE_ID = Regex("""^ex_added_(\d+)_\d+$""")

        /** Added on top of the day's plan (manual, AI, library or clipboard), so it has no target. */
        fun isAddedExercise(exerciseId: String): Boolean = ADDED_EXERCISE_ID.matches(exerciseId)

        private const val HISTORY_WINDOW_DAYS = 120
        private const val HISTORY_EXERCISE_LIMIT = 30
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val clipboardJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    fun getSetsByDate(date: String): Flow<List<WorkoutSet>> = dao.getSetsByDate(date)

    suspend fun getSetsByDateSync(date: String): List<WorkoutSet> = dao.getSetsByDateSync(date)

    /**
     * Loads workout for a date for display only. A saved day shows exactly its saved sets (the
     * preset only backfills blank metadata on matching rows), so a day planned from scratch does not
     * get preset exercises back. An unsaved day shows the preset. Does not persist.
     */
    suspend fun loadWorkoutForDateDisplay(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val existing = dao.getSetsByDateSync(date)
            if (existing.isNotEmpty()) {
                val saved = existing.filterNot { isRestDayAcknowledgment(it) }
                val blueprintById = loadBlueprintSetsForDate(date).getOrNull().orEmpty().associateBy { it.id }
                return@withContext Result.success(saved.map { it.backfilledFrom(blueprintById[it.id]) })
            }
            val blueprintSets = loadBlueprintSetsForDate(date).getOrElse { return@withContext Result.failure(it) }
            Result.success(mergeBlueprintWithExisting(blueprintSets, emptyList(), date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun presetContentOrNull(): String? = presetStorage.getPresetContent(PresetCategory.Workout)

    /** Sessions the active preset plans for [date]'s weekday; null when no preset is active. */
    suspend fun presetSessionsForDate(date: String): List<WorkoutDaySession>? {
        val content = presetContentOrNull() ?: return null
        val days = runCatching { json.decodeFromString<WeeklyWorkoutPlan>(content).weekly_plan }.getOrNull()
            ?: return null
        val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull()
            ?: return null
        return when (Calendar.getInstance().apply { time = parsed }.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> days.monday
            Calendar.TUESDAY -> days.tuesday
            Calendar.WEDNESDAY -> days.wednesday
            Calendar.THURSDAY -> days.thursday
            Calendar.FRIDAY -> days.friday
            Calendar.SATURDAY -> days.saturday
            else -> days.sunday
        }
    }

    fun encodeSessionsJson(sessions: List<WorkoutDaySession>): String =
        json.encodeToString(ListSerializer(WorkoutDaySession.serializer()), sessions)

    suspend fun deleteWorkoutForDate(date: String) = withContext(Dispatchers.IO) {
        dao.deleteSetsByDate(date)
    }

    /** Whether [date] has saved workout rows (including a rest-day acknowledgment). */
    suspend fun hasSavedWorkout(date: String): Boolean = dao.getSetsByDateSync(date).isNotEmpty()

    private fun WorkoutSet.backfilledFrom(blue: WorkoutSet?): WorkoutSet {
        if (blue == null) return this
        return copy(
            sessionTitle = sessionTitle.ifBlank { blue.sessionTitle },
            sessionDescription = sessionDescription.ifBlank { blue.sessionDescription },
            exerciseName = exerciseName.ifBlank { blue.exerciseName },
            exerciseDescription = exerciseDescription.ifBlank { blue.exerciseDescription },
            timing = timing ?: blue.timing,
            targetMuscles = targetMuscles.ifBlank { blue.targetMuscles },
        )
    }

    /**
     * Fetches only the preset from Drive for the given date (no DB read/write).
     * Use to replace current in-memory workout with latest preset when user taps Refresh from Drive.
     */
    suspend fun loadPresetOnlyForDate(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val blueprintSets = loadBlueprintSetsForDate(date).getOrElse { return@withContext Result.failure(it) }
            Result.success(mergeBlueprintWithExisting(blueprintSets, emptyList(), date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * When a date is selected and caller needs to persist immediately (legacy): load from Drive,
     * merge with existing Room data, then UPSERT into Room. Prefer loadWorkoutForDateDisplay for Calendar.
     */
    suspend fun loadBlueprintForDate(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val existing = dao.getSetsByDateSync(date)
            val blueprintSets = loadBlueprintSetsForDate(date).getOrElse { return@withContext Result.failure(it) }
            val merged = mergeBlueprintWithExisting(blueprintSets, existing, date)
            dao.insertSets(merged)
            Result.success(dao.getSetsByDateSync(date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches preset from active preset (Settings) and returns WorkoutSet list for the given date (day of week).
     * If no preset is configured, returns failure.
     */
    private suspend fun loadBlueprintSetsForDate(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val content = presetStorage.getPresetContent(PresetCategory.Workout)
                ?: return@withContext Result.failure(Exception("No workout preset active. Load a preset in Settings or from the Calendar screen."))
            ingestSessionsFromWeeklyPlan(content)
            parseWorkoutPlanContentToSets(content, date)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses preset JSON and returns workout sets for the given date. Used when applying a new preset.
     */
    fun getPresetSetsForDateFromContent(content: String, date: String): Result<List<WorkoutSet>> = try {
        parseWorkoutPlanContentToSets(content, date)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun ingestSessionsFromWeeklyPlan(content: String) {
        runCatching {
            val plan = json.decodeFromString<WeeklyWorkoutPlan>(content)
            val days = plan.weekly_plan
            libraryRepository.upsertExerciseSessions(
                days.monday + days.tuesday + days.wednesday + days.thursday +
                    days.friday + days.saturday + days.sunday
            )
        }
    }

    /**
     * Parses clipboard workouts for [mode]. A session is one item:
     * - [CalendarAiMode.SINGLE]: one session object, one exercise object, or an array of exercises
     *   (all treated as a single session).
     * - Other modes: an array of session objects or `{ "sessions": [...] }`.
     */
    fun parseClipboardToSessions(jsonString: String, mode: CalendarAiMode): Result<List<WorkoutDaySession>> = runCatching {
        val trimmed = jsonString.trim()
        require(trimmed.isNotEmpty()) { "Clipboard is empty" }
        val element = runCatching { json.parseToJsonElement(trimmed) }
            .getOrElse { throw IllegalArgumentException("Clipboard is not valid JSON") }
            .withDefaultLoadUnit(ExerciseImportDefaults.LOAD_UNIT)
        fun JsonElement.isSession() = (this as? JsonObject)?.containsKey("exercises") == true
        val wrapped = (element as? JsonObject)?.get("sessions") as? JsonArray
        val sessions = if (mode == CalendarAiMode.SINGLE) {
            when {
                wrapped != null || (element is JsonArray && element.any { it.isSession() }) ->
                    throw IllegalArgumentException("Clipboard holds several sessions. Use Add multiple instead.")
                element.isSession() -> listOf(json.decodeFromJsonElement(WorkoutDaySession.serializer(), element))
                element is JsonArray -> {
                    val exercises = element.map { json.decodeFromJsonElement(WorkoutExercisePlan.serializer(), it) }
                    listOf(exercisesAsSession(exercises))
                }
                element is JsonObject -> listOf(
                    exercisesAsSession(listOf(json.decodeFromJsonElement(WorkoutExercisePlan.serializer(), element)))
                )
                else -> throw IllegalArgumentException("Expected one workout session object")
            }
        } else {
            val items = when {
                wrapped != null -> wrapped
                element is JsonArray && element.all { it.isSession() } -> element
                element is JsonArray ->
                    throw IllegalArgumentException(
                        "This looks like one session's exercises. Use Add a single workout, or wrap sessions in an array."
                    )
                else -> throw IllegalArgumentException(
                    "Clipboard holds a single workout. Use a JSON array of sessions or { \"sessions\": [...] }."
                )
            }
            items.map { json.decodeFromJsonElement(WorkoutDaySession.serializer(), it) }
        }
        require(sessions.isNotEmpty() && sessions.all { it.exercises.isNotEmpty() }) {
            "Every session needs at least one exercise"
        }
        sessions
    }

    /**
     * Sets without `unitLoad` would decode as [com.vm.core.models.WorkoutSetPlan]'s "kg" default;
     * pasted JSON follows the import default instead, like AI and manual entry.
     */
    private fun JsonElement.withDefaultLoadUnit(unit: String, inSets: Boolean = false): JsonElement = when (this) {
        is JsonArray -> JsonArray(map { it.withDefaultLoadUnit(unit, inSets) })
        is JsonObject -> {
            val mapped = mapValues { (key, value) -> value.withDefaultLoadUnit(unit, inSets = key == "sets") }
            JsonObject(if (inSets && "unitLoad" !in mapped) mapped + ("unitLoad" to JsonPrimitive(unit)) else mapped)
        }
        else -> this
    }

    private fun exercisesAsSession(exercises: List<WorkoutExercisePlan>): WorkoutDaySession {
        val type = exercises.firstOrNull()?.type?.ifBlank { "RESISTANCE" } ?: "RESISTANCE"
        val title = exercises.singleOrNull()?.name?.ifBlank { null } ?: "Added"
        return WorkoutDaySession(title = title, type = type, exercises = exercises)
    }

    private fun parseWorkoutPlanContentToSets(content: String, date: String): Result<List<WorkoutSet>> = try {
        val plan = json.decodeFromString<WeeklyWorkoutPlan>(content)
        Result.success(WorkoutPlanRows.planToRows(plan, date))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun mergeBlueprintWithExisting(
        blueprint: List<WorkoutSet>,
        existing: List<WorkoutSet>,
        date: String
    ): List<WorkoutSet> {
        val existingById = existing.associateBy { it.id }
        val merged = blueprint.map { blue ->
            existingById[blue.id]?.let { kept ->
                blue.copy(
                    sessionTitle = kept.sessionTitle.ifBlank { blue.sessionTitle },
                    sessionDescription = kept.sessionDescription.ifBlank { blue.sessionDescription },
                    exerciseName = kept.exerciseName.ifBlank { blue.exerciseName },
                    exerciseDescription = kept.exerciseDescription.ifBlank { blue.exerciseDescription },
                    timing = kept.timing ?: blue.timing,
                    // The preset defines session shape, so it wins over whatever was saved earlier.
                    exerciseType = blue.exerciseType.ifBlank { kept.exerciseType },
                    groupingId = blue.groupingId ?: kept.groupingId,
                    workoutStatus = kept.workoutStatus ?: blue.workoutStatus,
                    targetMuscles = blue.targetMuscles.ifBlank { kept.targetMuscles },
                    actualLoad = kept.actualLoad,
                    actualReps = kept.actualReps,
                    actualVelocity = kept.actualVelocity,
                    actualRpe = kept.actualRpe,
                    actualRir = kept.actualRir,
                    actualDuration = kept.actualDuration,
                    actualDistance = kept.actualDistance,
                    actualRest = kept.actualRest,
                    actualCadence = kept.actualCadence,
                    actualTotalDuration = kept.actualTotalDuration,
                    isCompleted = kept.isCompleted
                )
            } ?: blue
        }
        // Sessions the user added on top of the preset exist only in Room, so carry them through
        // reloads instead of letting the blueprint drop them.
        val blueprintIds = blueprint.mapTo(mutableSetOf()) { it.id }
        val addedByUser = existing.filter {
            it.id !in blueprintIds && !isRestDayAcknowledgment(it)
        }
        return merged + addedByUser
    }

    suspend fun updateSet(entry: WorkoutSet) = dao.updateSet(entry)

    suspend fun insertSets(entries: List<WorkoutSet>) = dao.insertSets(entries)

    /**
     * Save Workout: replace all sets for date with the given list. Only saved state is in DB (Home completion uses this).
     * Saving with no sets stores a single acknowledgment row so rest / empty days still update Daily Inputs.
     */
    suspend fun saveWorkoutForDate(date: String, sets: List<WorkoutSet>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deleteSetsByDate(date)
            if (sets.isNotEmpty()) {
                dao.insertSets(sets)
            } else {
                dao.insertSets(listOf(restDayAcknowledgmentSet(date)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun restDayAcknowledgmentSet(date: String): WorkoutSet {
        val id = "${date}_${REST_DAY_ACK_EXERCISE_ID}_1"
        return WorkoutSet(
            id = id,
            exerciseId = REST_DAY_ACK_EXERCISE_ID,
            setNumber = 1,
            date = date,
            exerciseType = "REST_ACK",
            workoutStatus = "Done",
            isCompleted = true,
            sessionTitle = "",
            sessionDescription = "",
            exerciseName = "",
            exerciseDescription = "",
        )
    }

    /**
     * Merges sets received from Wear into the current workout for [date] (same rules as Calendar) and persists.
     * Called when the watch sends a completed workout so data is not lost if the user never taps Save Changes.
     */
    suspend fun mergeWearCompletionAndSave(date: String, wearSets: List<WorkoutSet>): Result<Unit> = withContext(Dispatchers.IO) {
        if (wearSets.isEmpty()) return@withContext Result.success(Unit)
        val displayResult = loadWorkoutForDateDisplay(date)
        if (displayResult.isFailure) return@withContext Result.failure(
            displayResult.exceptionOrNull() ?: Exception("Could not load workout for merge")
        )
        val sets = displayResult.getOrNull() ?: emptyList()
        val byId = sets.associateBy { it.id }.toMutableMap()
        wearSets.forEach { wear ->
            val base = byId[wear.id] ?: wear
            byId[wear.id] = base.mergeWearOverlay(wear)
        }
        saveWorkoutForDate(date, byId.values.toList())
    }

    /**
     * Compact summary of the user's recent training, used as grounding context for AI exercise import
     * so generated sessions reuse the same muscle tags, load unit and working weights.
     */
    suspend fun buildExerciseAiContext(date: String): ExerciseAiContext = withContext(Dispatchers.IO) {
        val recent = runCatching {
            dao.getSetsBetweenSync(shiftDate(date, -HISTORY_WINDOW_DAYS), date)
        }.getOrDefault(emptyList()).filterNot { isRestDayAcknowledgment(it) }

        val presetExercises = runCatching {
            presetStorage.getPresetContent(PresetCategory.Workout)?.let { content ->
                json.decodeFromString<WeeklyWorkoutPlan>(content).allExercises()
            }
        }.getOrNull().orEmpty()

        val vocabulary = buildList {
            presetExercises.forEach { exercise ->
                exercise.target.keys.forEach { tag -> normalizeMuscleTag(tag)?.let(::add) }
            }
            recent.forEach { set ->
                MuscleTargets.parse(set.targetMuscles).keys.forEach { tag -> normalizeMuscleTag(tag)?.let(::add) }
            }
            addAll(ExerciseImportDefaults.MUSCLES)
        }.distinct()

        val loadUnit = recent.filter { it.targetLoad != null || it.actualLoad != null }
            .groupingBy { normalizeLoadUnit(it.unitLoad) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: presetExercises.flatMap { it.sets }
                .filter { it.targetLoad != null }
                .groupingBy { normalizeLoadUnit(it.unitLoad) }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            ?: ExerciseImportDefaults.LOAD_UNIT

        ExerciseAiContext(
            muscleVocabulary = vocabulary,
            loadUnit = loadUnit,
            recentExercises = summarizeRecentExercises(recent),
        )
    }

    /** One line per distinct exercise, most recently trained first. */
    private fun summarizeRecentExercises(sets: List<WorkoutSet>): List<String> =
        sets.filter { it.exerciseName.isNotBlank() }
            .groupBy { it.exerciseName }
            .entries
            .sortedByDescending { (_, rows) -> rows.maxOf { it.date } }
            .take(HISTORY_EXERCISE_LIMIT)
            .map { (name, rows) ->
                val lastDate = rows.maxOf { it.date }
                val lastRows = rows.filter { it.date == lastDate }
                    .sortedWith(compareBy<WorkoutSet> { it.setNumber }.thenBy { it.dropNumber })
                val head = lastRows.first()
                val tags = MuscleTargets.parse(head.targetMuscles).map { (tag, weight) ->
                    if (weight >= MuscleTargets.PRIMARY) tag else "$tag $weight"
                }
                val mains = lastRows.filter { !it.isDropRow }
                val details = if (head.exerciseType.equals("RESISTANCE", ignoreCase = true)) {
                    val load = mains.mapNotNull { it.actualLoad ?: it.targetLoad }.maxOrNull()
                    val reps = mains.mapNotNull { it.actualReps ?: it.targetReps }.maxOrNull()
                    val drops = lastRows.count { it.isDropRow }
                    buildList {
                        add("${mains.size} sets")
                        if (load != null) add("$load ${head.unitLoad}")
                        if (reps != null) add("$reps reps")
                        mains.firstNotNullOfOrNull { it.targetRir }?.let { add("RIR $it") }
                    }.joinToString(" x ") + if (drops > 0) " + $drops drop(s)" else ""
                } else if (mains.any { it.isInterval }) {
                    summarizeIntervals(mains)
                } else {
                    val durationSec = head.actualDuration
                    val duration = if (durationSec != null) {
                        val unit = head.unitDuration.lowercase()
                        when {
                            unit == "min" || unit == "minutes" || unit == "minute" ->
                                "${(durationSec / 60).coerceAtLeast(0)} ${head.unitDuration}"
                            else -> "$durationSec ${head.unitDuration}"
                        }
                    } else {
                        head.targetDuration?.let { "$it ${head.unitDuration}" }
                    }
                    val distance = head.actualDistance ?: head.targetDistance
                    buildList {
                        if (duration != null) add(duration)
                        if (distance != null) add("$distance ${head.unitDistance}")
                    }.joinToString(", ").ifBlank { head.exerciseType.lowercase(Locale.US) }
                }
                val tagPart = if (tags.isEmpty()) "" else " [${tags.joinToString(", ")}]"
                "$name$tagPart — $details ($lastDate)"
            }

    /** e.g. "8 x 100 yd Z2" or "5 x 1 min Z2 108 rpm". */
    private fun summarizeIntervals(intervals: List<WorkoutSet>): String {
        val head = intervals.first()
        val work = head.targetDistance?.let { "${formatPlain(it)} ${head.unitDistance}" }
            ?: head.targetDuration?.let { "$it ${head.unitDuration}" }
            ?: "interval"
        return buildList {
            add("${intervals.size} x $work")
            head.targetZone?.let { add("Z$it") }
            head.targetCadence?.let { add("$it ${head.unitCadence}") }
            head.stroke?.let { add(it.lowercase(Locale.US)) }
        }.joinToString(" ")
    }

    private fun formatPlain(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun WeeklyWorkoutPlan.allExercises(): List<WorkoutExercisePlan> = with(weekly_plan) {
        listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday)
            .flatten()
            .flatMap { it.exercises }
    }

    private fun shiftDate(date: String, days: Int): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = runCatching { format.parse(date) }.getOrNull() ?: return date
        val cal = Calendar.getInstance(Locale.US).apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, days)
        }
        return format.format(cal.time)
    }

    /**
     * Converts reviewed sessions into rows for [date]. Exercise ids continue after any user-added
     * session already on the day so repeated adds never overwrite each other.
     */
    fun convertSessionsToWorkoutSets(
        date: String,
        sessions: List<WorkoutDaySession>,
        existingSets: List<WorkoutSet>,
    ): Result<List<WorkoutSet>> = try {
        val base = nextAddedSessionIndex(existingSets)
        val sets = sessions.withIndex().flatMap { (index, session) ->
            sessionToWorkoutSets(date, session, base + index)
        }
        if (sets.isEmpty()) Result.failure(Exception("No exercises or sets to add"))
        else Result.success(sets)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Rows for a whole re-planned day (plan modes). Ids are `ex_plan_{session}_{exercise}`: they are
     * the day's plan, so they count toward its targets, but never collide with preset ids.
     */
    fun convertPlannedDayToWorkoutSets(date: String, sessions: List<WorkoutDaySession>): Result<List<WorkoutSet>> =
        runCatching {
            sessions.withIndex()
                .flatMap { (index, session) -> WorkoutPlanRows.sessionToRows(date, session, idPrefix = "ex_plan_$index") }
                .also { require(it.isNotEmpty()) { "No exercises or sets to add" } }
        }

    /** Lowest unused index in the `ex_added_{index}_{position}` id space for this day. */
    private fun nextAddedSessionIndex(existingSets: List<WorkoutSet>): Int {
        val used = existingSets.mapNotNull { ADDED_EXERCISE_ID.find(it.exerciseId)?.groupValues?.get(1)?.toIntOrNull() }
        return (used.maxOrNull() ?: -1) + 1
    }

    /**
     * Serializes one session (all sets for that session) as [WorkoutDaySession] JSON, matching
     * [parseAndConvertToWorkoutSets] so it can be pasted into "Add exercise from JSON".
     */
    fun encodeSessionForClipboard(sessionSets: List<WorkoutSet>): String {
        val realSets = sessionSets.filter { !isRestDayAcknowledgment(it) }
        if (realSets.isEmpty()) return "{}"
        return clipboardJson.encodeToString(WorkoutDaySession.serializer(), WorkoutPlanRows.rowsToSession(realSets))
    }

    fun parseAndConvertToWorkoutSets(
        date: String,
        jsonString: String,
        existingSets: List<WorkoutSet> = emptyList(),
    ): Result<List<WorkoutSet>> {
        return try {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) return Result.failure(Exception("Empty JSON"))
            val sessionIndex = nextAddedSessionIndex(existingSets)
            val sets = when {
                trimmed.startsWith("{") && trimmed.contains("exercises") -> {
                    val session = json.decodeFromString<WorkoutDaySession>(trimmed)
                    sessionToWorkoutSets(date, session, sessionIndex)
                }
                trimmed.startsWith("[") -> {
                    val exercises = json.decodeFromString<List<WorkoutExercisePlan>>(trimmed)
                    exercisesToWorkoutSets(date, exercises, sessionIndex)
                }
                else -> {
                    val exercise = json.decodeFromString<WorkoutExercisePlan>(trimmed)
                    exercisesToWorkoutSets(date, listOf(exercise), sessionIndex)
                }
            }
            if (sets.isEmpty()) Result.failure(Exception("No exercises or sets in JSON"))
            else Result.success(sets)
        } catch (e: Exception) {
            Result.failure(Exception("Invalid JSON: ${e.message}"))
        }
    }

    private fun sessionToWorkoutSets(date: String, session: WorkoutDaySession, sessionIndex: Int): List<WorkoutSet> =
        WorkoutPlanRows.sessionToRows(date, session, idPrefix = "ex_added_$sessionIndex")

    private fun exercisesToWorkoutSets(
        date: String,
        exercises: List<WorkoutExercisePlan>,
        sessionIndex: Int,
    ): List<WorkoutSet> {
        val type = exercises.firstOrNull()?.type?.ifBlank { null } ?: "RESISTANCE"
        val session = WorkoutDaySession(title = "Added", type = type, exercises = exercises)
        return sessionToWorkoutSets(date, session, sessionIndex)
    }
}

/**
 * Overlay fields received from Wear onto the phone's merged display row so preset metadata and units
 * stay intact if the wire payload omits or truncates optional JSON fields.
 *
 * Completion comes from [WorkoutSet.isCompleted] only: the watch marks a set done when it is
 * concluded, so values touched on an unfinished set never count as Done.
 * [WorkoutSet.actualDuration] is always **seconds**.
 */
internal fun WorkoutSet.mergeWearOverlay(wear: WorkoutSet): WorkoutSet {
    if (id != wear.id) return this
    return copy(
        actualLoad = wear.actualLoad ?: actualLoad,
        actualReps = wear.actualReps ?: actualReps,
        actualDuration = wear.actualDuration ?: actualDuration,
        actualDistance = wear.actualDistance ?: actualDistance,
        actualVelocity = wear.actualVelocity ?: actualVelocity,
        actualRpe = wear.actualRpe ?: actualRpe,
        actualRir = wear.actualRir ?: actualRir,
        actualRest = wear.actualRest ?: actualRest,
        actualCadence = wear.actualCadence ?: actualCadence,
        actualTotalDuration = wear.actualTotalDuration ?: actualTotalDuration,
        workoutStatus = when {
            wear.workoutStatus != null -> wear.workoutStatus
            wear.isCompleted -> "Done"
            else -> workoutStatus
        },
        isCompleted = isCompleted || wear.isCompleted
    )
}
