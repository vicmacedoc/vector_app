package com.vm.vector.data

import com.vm.core.models.WorkoutDaySession
import com.vm.core.models.WorkoutDropPlan
import com.vm.core.models.WorkoutExercisePlan
import com.vm.core.models.WorkoutIntervalPlan
import com.vm.core.models.WorkoutSetPlan
import com.vm.core.models.WorkoutSetType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Session shape supported by the Exercise calendar. Drives which fields are meaningful:
 * - RESISTANCE: working sets (optionally with drops) + rest; exercises may share a superset group
 * - ENDURANCE: a sport (RUN / BIKE / SWIM) whose exercises are lists of intervals
 * - OTHER: one or more named activities, each with a total duration and optional rest after it
 */
enum class ExerciseKind { RESISTANCE, ENDURANCE, OTHER }

object ExerciseImportDefaults {
    const val LOAD_UNIT = "lbs"
    const val REST_UNIT = "s"
    /** Per-set timed work (planks, carries) is stored in seconds like the presets. */
    const val SET_DURATION_UNIT = "s"
    const val EXERCISE_DURATION_UNIT = "min"
    const val INTERVAL_DURATION_UNIT = "min"
    const val DISTANCE_UNIT = "km"
    const val CADENCE_UNIT = "rpm"

    val LOAD_UNITS = listOf("lbs", "kg")
    val REST_UNITS = listOf("s", "min")
    val DURATION_UNITS = listOf("min", "s")
    val DISTANCE_UNITS = listOf("km", "mi", "m", "yd")
    val SPORTS = listOf("RUN", "BIKE", "SWIM")
    val STROKES = listOf("FREE", "BACK", "BREAST", "FLY", "IM", "CHOICE", "DRILL", "KICK")
    val SUPERSET_GROUPS = listOf("A", "B", "C", "D")
    const val MAX_ZONE = 5

    /** Fallback muscle tags when neither the active preset nor logged history offers a vocabulary. */
    val MUSCLES = listOf(
        "chest", "back", "shoulder", "triceps", "biceps", "forearms", "traps",
        "quads", "hamstrings", "glutes", "calves", "adductors", "core", "neck",
    )
}

/** A lighter follow-up done straight after its main set, with no rest. */
data class ExerciseDropDraft(
    val description: String = "",
    val targetLoad: Double? = null,
    /** Null inherits the parent set's unit. */
    val unitLoad: String? = null,
    val targetReps: Int? = null,
    val targetRir: Int? = null,
)

/** One prescribed working set of a resistance exercise. Set numbers come from list position. */
data class ExerciseSetDraft(
    val description: String = "",
    val targetLoad: Double? = null,
    val unitLoad: String = ExerciseImportDefaults.LOAD_UNIT,
    val targetReps: Int? = null,
    val targetRir: Int? = null,
    val rest: Int? = null,
    val restUnit: String = ExerciseImportDefaults.REST_UNIT,
    val targetDuration: Int? = null,
    val unitDuration: String = ExerciseImportDefaults.SET_DURATION_UNIT,
    val drops: List<ExerciseDropDraft> = emptyList(),
) {
    val isDrop: Boolean get() = drops.isNotEmpty()
}

/** One endurance work block: either a duration or a distance, plus optional intensity and rest. */
data class ExerciseIntervalDraft(
    val description: String = "",
    val targetDuration: Int? = null,
    val unitDuration: String = ExerciseImportDefaults.INTERVAL_DURATION_UNIT,
    val targetDistance: Double? = null,
    val unitDistance: String = ExerciseImportDefaults.DISTANCE_UNIT,
    val targetZone: Int? = null,
    val targetRpe: Double? = null,
    val targetCadence: Int? = null,
    val unitCadence: String = ExerciseImportDefaults.CADENCE_UNIT,
    val rest: Int? = null,
    val restUnit: String = ExerciseImportDefaults.REST_UNIT,
)

/**
 * One exercise awaiting review.
 * - RESISTANCE: [sets] (+ drops), [targets], [supersetGroup], [timing]
 * - ENDURANCE: [intervals], [stroke] for swims
 * - OTHER: [totalDuration] and optional [rest] after it
 * Fields that don't apply to the session kind are kept while reviewing so toggling the kind is
 * non-destructive, and dropped on convert.
 */
data class ExerciseImportDraft(
    val name: String = "",
    val description: String = "",
    /** Muscle -> volume weight per set; insertion order is display order (primary first). */
    val targets: Map<String, Double> = emptyMap(),
    val supersetGroup: String? = null,
    val timing: String? = null,
    val totalDuration: Double? = null,
    val unitDuration: String = ExerciseImportDefaults.EXERCISE_DURATION_UNIT,
    val stroke: String? = null,
    /** Rest after this exercise (OTHER sequenced activities). */
    val rest: Int? = null,
    val restUnit: String = ExerciseImportDefaults.REST_UNIT,
    val sets: List<ExerciseSetDraft> = emptyList(),
    val intervals: List<ExerciseIntervalDraft> = emptyList(),
)

/** A session tile awaiting review. All exercises in a session share the session [kind]. */
data class ExerciseSessionDraft(
    val title: String = "",
    val description: String = "",
    val kind: ExerciseKind = ExerciseKind.RESISTANCE,
    /** ENDURANCE only: RUN | BIKE | SWIM. */
    val sport: String? = null,
    val exercises: List<ExerciseImportDraft> = emptyList(),
)

/** Context handed to the model so it reuses the user's own muscle tags, units and loads. */
data class ExerciseAiContext(
    val muscleVocabulary: List<String> = ExerciseImportDefaults.MUSCLES,
    val loadUnit: String = ExerciseImportDefaults.LOAD_UNIT,
    val recentExercises: List<String> = emptyList(),
    val existingSessionTitles: List<String> = emptyList(),
)

/** Review is one step per session plus one step per exercise inside it. */
sealed interface ExerciseImportStep {
    val sessionIndex: Int

    data class Session(override val sessionIndex: Int) : ExerciseImportStep
    data class Exercise(override val sessionIndex: Int, val exerciseIndex: Int) : ExerciseImportStep
}

fun List<ExerciseSessionDraft>.importSteps(): List<ExerciseImportStep> = buildList {
    this@importSteps.forEachIndexed { sessionIndex, session ->
        add(ExerciseImportStep.Session(sessionIndex))
        session.exercises.indices.forEach { exerciseIndex ->
            add(ExerciseImportStep.Exercise(sessionIndex, exerciseIndex))
        }
    }
}

fun parseExerciseKindOrDefault(
    raw: String?,
    fallback: ExerciseKind = ExerciseKind.RESISTANCE,
): ExerciseKind {
    val value = raw.asOptionalImportValue() ?: return fallback
    return runCatching { ExerciseKind.valueOf(value.uppercase(Locale.US)) }.getOrDefault(fallback)
}

fun normalizeMuscleTag(raw: String?): String? =
    raw.asOptionalImportValue()?.lowercase(Locale.US)?.replace(' ', '_')

/** Normalizes tags, keeps the first-seen order and the highest weight for duplicates. */
fun normalizeMuscleTargets(raw: Map<String, Double>): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    raw.forEach { (tag, weight) ->
        val key = normalizeMuscleTag(tag) ?: return@forEach
        if (!weight.isFinite() || weight <= 0.0) return@forEach
        val rounded = (weight.coerceAtMost(MAX_MUSCLE_WEIGHT) * 100).roundToInt() / 100.0
        out[key] = maxOf(out[key] ?: 0.0, rounded)
    }
    return out
}

private const val MAX_MUSCLE_WEIGHT = 2.0

fun normalizeSupersetGroup(raw: String?): String? =
    raw.asOptionalImportValue()?.uppercase(Locale.US)?.firstOrNull { it.isLetter() }?.toString()

fun normalizeSport(raw: String?): String? =
    when (raw.asOptionalImportValue()?.lowercase(Locale.US)) {
        "run", "running", "jog", "jogging" -> "RUN"
        "bike", "biking", "cycle", "cycling", "ride", "spin" -> "BIKE"
        "swim", "swimming" -> "SWIM"
        else -> null
    }

fun normalizeStroke(raw: String?): String? {
    val value = raw.asOptionalImportValue()?.uppercase(Locale.US) ?: return null
    return when (value) {
        "FREESTYLE", "CRAWL" -> "FREE"
        "BACKSTROKE" -> "BACK"
        "BREASTSTROKE" -> "BREAST"
        "BUTTERFLY" -> "FLY"
        "MEDLEY", "INDIVIDUAL MEDLEY" -> "IM"
        "ANY" -> "CHOICE"
        else -> value.takeIf { it in ExerciseImportDefaults.STROKES }
    }
}

fun normalizeLoadUnit(raw: String?, fallback: String = ExerciseImportDefaults.LOAD_UNIT): String =
    when (raw.asOptionalImportValue()?.lowercase(Locale.US)) {
        "lb", "lbs", "pound", "pounds" -> "lbs"
        "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms" -> "kg"
        else -> fallback
    }

fun normalizeShortTimeUnit(raw: String?, fallback: String): String =
    when (raw.asOptionalImportValue()?.lowercase(Locale.US)) {
        "s", "sec", "secs", "second", "seconds" -> "s"
        "m", "min", "mins", "minute", "minutes" -> "min"
        else -> fallback
    }

fun normalizeDistanceUnit(raw: String?, fallback: String = ExerciseImportDefaults.DISTANCE_UNIT): String =
    when (raw.asOptionalImportValue()?.lowercase(Locale.US)) {
        "km", "kms", "kilometer", "kilometers", "kilometre", "kilometres" -> "km"
        "mi", "mile", "miles" -> "mi"
        "m", "meter", "meters", "metre", "metres" -> "m"
        "yd", "yds", "yard", "yards" -> "yd"
        else -> fallback
    }

/** Tempo notation is eccentric-pause-concentric-pause, e.g. "3-1-1-0". */
fun normalizeTiming(raw: String?): String? {
    val value = raw.asOptionalImportValue() ?: return null
    val parts = value.split('-', ':', '/').map { it.trim() }
    if (parts.size !in 3..4 || parts.any { it.toIntOrNull() == null }) return null
    return parts.joinToString("-")
}

/** RPE is logged in half steps on a 0-10 scale. */
fun normalizeRpe(raw: Double?): Double? =
    raw?.takeIf { it.isFinite() }?.coerceIn(0.0, 10.0)?.let { (it * 2).roundToInt() / 2.0 }

/** Aligns every exercise with its session kind so conversion and rendering agree. */
fun List<ExerciseSessionDraft>.normalizedForImport(): List<ExerciseSessionDraft> =
    map { session ->
        session.copy(
            title = session.title.trim().ifBlank { defaultSessionTitle(session.kind) },
            sport = if (session.kind == ExerciseKind.ENDURANCE) normalizeSport(session.sport) else null,
            exercises = session.exercises.map { it.normalizedFor(session.kind) },
        )
    }

fun defaultSessionTitle(kind: ExerciseKind): String = when (kind) {
    ExerciseKind.RESISTANCE -> "Resistance: Added session"
    ExerciseKind.ENDURANCE -> "Endurance: Added session"
    ExerciseKind.OTHER -> "Other: Added session"
}

private fun ExerciseImportDraft.normalizedFor(kind: ExerciseKind): ExerciseImportDraft = when (kind) {
    ExerciseKind.RESISTANCE -> copy(
        targets = normalizeMuscleTargets(targets),
        supersetGroup = normalizeSupersetGroup(supersetGroup),
        timing = normalizeTiming(timing),
        rest = null,
    )
    ExerciseKind.ENDURANCE -> copy(
        targets = emptyMap(),
        supersetGroup = null,
        timing = null,
        stroke = normalizeStroke(stroke),
        rest = null,
        intervals = intervals.map { it.normalized() },
    )
    ExerciseKind.OTHER -> copy(
        targets = emptyMap(),
        supersetGroup = null,
        timing = null,
        totalDuration = totalDuration ?: intervalMinutes(),
        unitDuration = if (totalDuration == null) "min" else unitDuration,
        restUnit = normalizeShortTimeUnit(restUnit, ExerciseImportDefaults.REST_UNIT),
    )
}

private fun ExerciseIntervalDraft.normalized(): ExerciseIntervalDraft = copy(
    unitDuration = normalizeShortTimeUnit(unitDuration, ExerciseImportDefaults.INTERVAL_DURATION_UNIT),
    unitDistance = normalizeDistanceUnit(unitDistance),
    targetZone = targetZone?.coerceIn(1, ExerciseImportDefaults.MAX_ZONE),
    targetRpe = normalizeRpe(targetRpe),
    restUnit = normalizeShortTimeUnit(restUnit, ExerciseImportDefaults.REST_UNIT),
)

/** Planned minutes from interval durations and rests; null when no interval is timed. */
fun ExerciseImportDraft.intervalMinutes(): Double? {
    val minutes = intervals.sumOf { interval ->
        val work = interval.targetDuration?.let { durationValueToMinutes(it.toDouble(), interval.unitDuration) } ?: 0.0
        val rest = interval.rest?.let { durationValueToMinutes(it.toDouble(), interval.restUnit) } ?: 0.0
        work + rest
    }
    return minutes.takeIf { it > 0.0 }
}

private fun durationValueToMinutes(value: Double, unit: String): Double =
    if (unit == "s") value / 60.0 else value

fun ExerciseSessionDraft.validationError(): String? {
    if (title.trim().isBlank()) return "Session title is required"
    if (exercises.isEmpty()) return "Session has no exercises"
    return null
}

fun ExerciseImportDraft.validationError(kind: ExerciseKind): String? {
    if (name.trim().isBlank()) return "Exercise name is required"
    return when (kind) {
        ExerciseKind.RESISTANCE -> when {
            sets.isEmpty() -> "Add at least one set, or move this exercise to an Endurance/Other session"
            sets.any { it.targetReps == null && it.targetDuration == null } ->
                "Every set needs reps or a duration"
            sets.any { set -> set.drops.any { it.targetReps == null } } -> "Every drop needs reps"
            else -> null
        }
        ExerciseKind.ENDURANCE -> when {
            intervals.isEmpty() -> "Add at least one interval"
            intervals.any { it.targetDuration == null && it.targetDistance == null } ->
                "Every interval needs a duration or a distance"
            else -> null
        }
        ExerciseKind.OTHER -> when {
            totalDuration == null || totalDuration <= 0.0 -> "Set a total duration"
            else -> null
        }
    }
}

fun WorkoutDaySession.toExerciseSessionDraft(): ExerciseSessionDraft {
    val kind = parseExerciseKindOrDefault(type)
    return ExerciseSessionDraft(
        title = title,
        description = description,
        kind = kind,
        sport = normalizeSport(sport),
        exercises = exercises.map { it.toExerciseImportDraft(kind) },
    )
}

private fun WorkoutExercisePlan.toExerciseImportDraft(kind: ExerciseKind): ExerciseImportDraft {
    val total = totalDuration ?: targetDuration?.toDouble()
    val legacyInterval = if (kind == ExerciseKind.ENDURANCE && intervals.isEmpty() &&
        (targetDuration != null || targetDistance != null)
    ) {
        ExerciseIntervalDraft(
            targetDuration = targetDuration ?: totalDuration?.roundToInt(),
            unitDuration = normalizeShortTimeUnit(durationUnit, ExerciseImportDefaults.INTERVAL_DURATION_UNIT),
            targetDistance = targetDistance,
            unitDistance = normalizeDistanceUnit(unitDistance),
            targetRpe = normalizeRpe(targetRpe),
        )
    } else {
        null
    }
    return ExerciseImportDraft(
        name = name,
        description = description,
        targets = target,
        supersetGroup = normalizeSupersetGroup(supersetGroup),
        timing = timing,
        totalDuration = total,
        unitDuration = normalizeShortTimeUnit(durationUnit, ExerciseImportDefaults.EXERCISE_DURATION_UNIT),
        stroke = normalizeStroke(stroke),
        rest = rest,
        restUnit = restUnit,
        sets = sets.map { set ->
            ExerciseSetDraft(
                description = set.description,
                targetLoad = set.targetLoad,
                unitLoad = set.unitLoad,
                targetReps = set.targetReps,
                targetRir = set.targetRir,
                rest = set.rest,
                restUnit = set.restUnit,
                targetDuration = set.targetDuration,
                unitDuration = set.unitDuration,
                drops = set.drops.map { drop ->
                    ExerciseDropDraft(
                        description = drop.description,
                        targetLoad = drop.targetLoad,
                        unitLoad = drop.unitLoad,
                        targetReps = drop.targetReps,
                        targetRir = drop.targetRir,
                    )
                },
            )
        },
        intervals = legacyInterval?.let(::listOf) ?: intervals.map { interval ->
            ExerciseIntervalDraft(
                description = interval.description,
                targetDuration = interval.targetDuration,
                unitDuration = normalizeShortTimeUnit(interval.unitDuration, "s"),
                targetDistance = interval.targetDistance,
                unitDistance = normalizeDistanceUnit(interval.unitDistance),
                targetZone = interval.targetZone,
                targetRpe = interval.targetRpe,
                targetCadence = interval.targetCadence,
                unitCadence = interval.unitCadence,
                rest = interval.rest,
                restUnit = normalizeShortTimeUnit(interval.restUnit, ExerciseImportDefaults.REST_UNIT),
            )
        },
    )
}

fun ExerciseSessionDraft.toWorkoutDaySession(): WorkoutDaySession {
    val sessionSport = if (kind == ExerciseKind.ENDURANCE) normalizeSport(sport) else null
    return WorkoutDaySession(
        title = title.trim().ifBlank { defaultSessionTitle(kind) },
        description = description.trim(),
        type = kind.name,
        sport = sessionSport,
        exercises = exercises.map { it.toWorkoutExercisePlan(kind, sessionSport) },
    )
}

private fun ExerciseImportDraft.toWorkoutExercisePlan(kind: ExerciseKind, sport: String?): WorkoutExercisePlan {
    val isResistance = kind == ExerciseKind.RESISTANCE
    val isEndurance = kind == ExerciseKind.ENDURANCE
    val total = totalDuration?.takeIf { it > 0.0 }
    return WorkoutExercisePlan(
        name = name.trim().ifBlank { "Exercise" },
        type = kind.name,
        target = if (isResistance) normalizeMuscleTargets(targets) else emptyMap(),
        supersetGroup = if (isResistance) normalizeSupersetGroup(supersetGroup) else null,
        description = description.trim(),
        timing = if (isResistance) normalizeTiming(timing) else null,
        totalDuration = total,
        unitDuration = total?.let {
            normalizeShortTimeUnit(unitDuration, ExerciseImportDefaults.EXERCISE_DURATION_UNIT)
        },
        stroke = if (isEndurance && sport == "SWIM") normalizeStroke(stroke) else null,
        rest = if (kind == ExerciseKind.OTHER) rest else null,
        restUnit = normalizeShortTimeUnit(restUnit, ExerciseImportDefaults.REST_UNIT),
        targetRpe = null,
        sets = if (isResistance) {
            sets.mapIndexed { index, set -> set.toWorkoutSetPlan(index + 1) }
        } else {
            emptyList()
        },
        intervals = if (isEndurance) {
            intervals.mapIndexed { index, interval -> interval.toWorkoutIntervalPlan(index + 1) }
        } else {
            emptyList()
        },
    )
}

private fun ExerciseSetDraft.toWorkoutSetPlan(setNumber: Int): WorkoutSetPlan {
    val setUnit = normalizeLoadUnit(unitLoad)
    return WorkoutSetPlan(
        setNumber = setNumber,
        description = description.trim(),
        cadence = null,
        setType = if (isDrop) WorkoutSetType.DROP else WorkoutSetType.NORMAL,
        targetLoad = targetLoad,
        unitLoad = setUnit,
        targetReps = targetReps,
        targetVelocity = null,
        targetDuration = targetDuration,
        unitDuration = normalizeShortTimeUnit(unitDuration, ExerciseImportDefaults.SET_DURATION_UNIT),
        targetDistance = null,
        targetRir = targetRir,
        drops = drops.mapIndexed { index, drop ->
            WorkoutDropPlan(
                dropNumber = index + 1,
                description = drop.description.trim(),
                targetLoad = drop.targetLoad,
                unitLoad = drop.unitLoad?.let { normalizeLoadUnit(it, setUnit) }?.takeIf { it != setUnit },
                targetReps = drop.targetReps,
                targetRir = drop.targetRir,
            )
        },
        rest = rest,
        restUnit = normalizeShortTimeUnit(restUnit, ExerciseImportDefaults.REST_UNIT),
    )
}

private fun ExerciseIntervalDraft.toWorkoutIntervalPlan(intervalNumber: Int): WorkoutIntervalPlan =
    WorkoutIntervalPlan(
        intervalNumber = intervalNumber,
        description = description.trim(),
        targetDuration = targetDuration,
        unitDuration = normalizeShortTimeUnit(unitDuration, ExerciseImportDefaults.INTERVAL_DURATION_UNIT),
        targetDistance = targetDistance,
        unitDistance = normalizeDistanceUnit(unitDistance),
        targetZone = targetZone?.coerceIn(1, ExerciseImportDefaults.MAX_ZONE),
        targetRpe = normalizeRpe(targetRpe),
        targetCadence = targetCadence,
        unitCadence = unitCadence.ifBlank { ExerciseImportDefaults.CADENCE_UNIT },
        rest = rest,
        restUnit = normalizeShortTimeUnit(restUnit, ExerciseImportDefaults.REST_UNIT),
    )
