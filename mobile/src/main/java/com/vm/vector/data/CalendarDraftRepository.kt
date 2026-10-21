package com.vm.vector.data

import com.vm.core.models.ActivityEntry
import com.vm.core.models.CalendarDraft
import com.vm.core.models.CalendarDraftKey
import com.vm.core.models.FoodEntry
import com.vm.core.models.SleepEntry
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Routine draft: the day's activities plus both sleep rows shown on day D (D−1 and D). */
@Serializable
data class RoutineDraftPayload(
    val activities: List<ActivityEntry> = emptyList(),
    val previousNight: SleepEntry? = null,
    val tonight: SleepEntry? = null,
)

@Serializable
data class FoodDraftPayload(
    val entries: List<FoodEntry> = emptyList(),
)

@Serializable
data class ExerciseDraftPayload(
    val sets: List<WorkoutSet> = emptyList(),
)

/** Self draft: scale levels keyed by `SelfScale.name`, the text fields and a pending audio note. */
@Serializable
data class SelfDraftPayload(
    val scales: Map<String, Int> = emptyMap(),
    val intention: String = "",
    val highlight: String = "",
    val lowlight: String = "",
    val gratitude: String = "",
    val journalText: String = "",
    val audioPath: String? = null,
    val audioDriveFileId: String? = null,
)

/**
 * Persists unsaved Calendar edits per category/day in `calendar_drafts`.
 * Callers write a draft only while state differs from its baseline (see [isSameAs] helpers).
 */
class CalendarDraftRepository(database: VectorDatabase) {
    private val dao = database.calendarDraftDao()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeKeysBetween(startDate: String, endDate: String): Flow<List<CalendarDraftKey>> =
        dao.observeKeysBetween(startDate, endDate)

    suspend fun getKeysBetween(startDate: String, endDate: String): List<CalendarDraftKey> =
        dao.getKeysBetween(startDate, endDate)

    suspend fun hasDraft(category: String, date: String): Boolean = dao.get(category, date) != null

    suspend fun delete(category: String, date: String) = dao.delete(category, date)

    suspend fun loadRoutine(date: String): RoutineDraftPayload? =
        load(CalendarDraft.CATEGORY_ROUTINE, date, RoutineDraftPayload.serializer())

    suspend fun loadFood(date: String): FoodDraftPayload? =
        load(CalendarDraft.CATEGORY_FOOD, date, FoodDraftPayload.serializer())

    suspend fun loadExercise(date: String): ExerciseDraftPayload? =
        load(CalendarDraft.CATEGORY_EXERCISE, date, ExerciseDraftPayload.serializer())

    suspend fun loadSelf(date: String): SelfDraftPayload? =
        load(CalendarDraft.CATEGORY_SELF, date, SelfDraftPayload.serializer())

    suspend fun saveRoutine(date: String, payload: RoutineDraftPayload) =
        save(CalendarDraft.CATEGORY_ROUTINE, date, payload, RoutineDraftPayload.serializer())

    suspend fun saveFood(date: String, payload: FoodDraftPayload) =
        save(CalendarDraft.CATEGORY_FOOD, date, payload, FoodDraftPayload.serializer())

    suspend fun saveExercise(date: String, payload: ExerciseDraftPayload) =
        save(CalendarDraft.CATEGORY_EXERCISE, date, payload, ExerciseDraftPayload.serializer())

    suspend fun saveSelf(date: String, payload: SelfDraftPayload) =
        save(CalendarDraft.CATEGORY_SELF, date, payload, SelfDraftPayload.serializer())

    private suspend fun <T> load(category: String, date: String, serializer: KSerializer<T>): T? {
        val draft = dao.get(category, date) ?: return null
        return runCatching { json.decodeFromString(serializer, draft.payloadJson) }
            .getOrElse {
                // Unreadable payloads (schema drift) are dropped rather than blocking the day.
                dao.delete(category, date)
                null
            }
    }

    private suspend fun <T> save(category: String, date: String, payload: T, serializer: KSerializer<T>) {
        dao.upsert(
            CalendarDraft(
                category = category,
                date = date,
                payloadJson = json.encodeToString(serializer, payload),
            )
        )
    }
}

/** Equality used for dirty checks: ignores row order and edit timestamps. */
fun RoutineDraftPayload.isSameAs(other: RoutineDraftPayload): Boolean =
    activities.normalizedActivities() == other.activities.normalizedActivities() &&
        previousNight.normalizedSleep() == other.previousNight.normalizedSleep() &&
        tonight.normalizedSleep() == other.tonight.normalizedSleep()

fun FoodDraftPayload.isSameAs(other: FoodDraftPayload): Boolean =
    entries.map { it.copy(timestamp = 0L) }.sortedBy { it.id } ==
        other.entries.map { it.copy(timestamp = 0L) }.sortedBy { it.id }

fun ExerciseDraftPayload.isSameAs(other: ExerciseDraftPayload): Boolean =
    sets.sortedBy { it.id } == other.sets.sortedBy { it.id }

private fun List<ActivityEntry>.normalizedActivities(): List<ActivityEntry> =
    map { it.copy(timestamp = 0L) }.sortedBy { it.id }

/** A sleep row with no fields set is the same as no row. */
private fun SleepEntry?.normalizedSleep(): SleepEntry? {
    val entry = this?.copy(timestamp = 0L) ?: return null
    return if (entry == SleepEntry(date = entry.date, timestamp = 0L)) null else entry
}
