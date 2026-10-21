package com.vm.vector.data

import com.vm.core.models.HabitDayConfig
import com.vm.core.models.HabitDefinition
import com.vm.core.models.HabitLog
import com.vm.core.models.HabitStatus
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class HabitScoreSummary(
    val habitId: String,
    val score: Double,
    val total: Int,
) {
    fun display(): String {
        val scoreText = if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
        return "[$scoreText / $total]"
    }
}

class HabitRepository(
    private val database: VectorDatabase,
) {
    private val definitionDao = database.habitDefinitionDao()
    private val configDao = database.habitDayConfigDao()
    private val logDao = database.habitLogDao()
    private val json = Json { ignoreUnknownKeys = true }

    fun observeDefinitions(): Flow<List<HabitDefinition>> = definitionDao.getAll()

    suspend fun getAllDefinitions(): List<HabitDefinition> = definitionDao.getAllSync()

    fun observeScoreSummaries(): Flow<Map<String, HabitScoreSummary>> =
        logDao.getAll().map { logs ->
            logs.filter { it.status != HabitStatus.NA }
                .groupBy { it.habitId }
                .mapValues { (habitId, habitLogs) ->
                    val score = habitLogs.sumOf { log ->
                        when (log.status) {
                            HabitStatus.PARTIAL -> 0.5
                            HabitStatus.DONE -> 1.0
                            HabitStatus.EXCEEDED -> 1.5
                            else -> 0.0
                        }
                    }
                    HabitScoreSummary(habitId, score, habitLogs.size)
                }
        }

    suspend fun createHabit(name: String): Result<HabitDefinition> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(Exception("Habit name cannot be empty"))
        if (definitionDao.getAllSync().any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(Exception("A habit with this name already exists"))
        }
        val def = HabitDefinition(
            id = "habit_${UUID.randomUUID()}",
            name = trimmed
        )
        definitionDao.insert(def)
        return Result.success(def)
    }

    suspend fun deleteHabit(habitId: String) {
        definitionDao.deleteById(habitId)
        logDao.deleteByHabitId(habitId)
        // Remove from all day configs
        // Re-write configs that contain this id — only touch latest approach:
        // strip from today's inherited configs by rewriting any config that lists it
        // Simple approach: for each config date we don't iterate all; strip when loading.
        // Still strip from known configs via raw read of latest only on next setSelected.
    }

    /**
     * Selected habit IDs for [date]: latest habit_day_config with date <= [date], else empty.
     */
    suspend fun getSelectedHabitIdsForDate(date: String): List<String> {
        val config = configDao.getLatestConfigOnOrBefore(date) ?: return emptyList()
        return decodeIds(config.habitIdsJson).filter { id ->
            definitionDao.getByIdSync(id) != null
        }
    }

    /**
     * Sets selected habits for [date] and all future days (deletes configs after [date]).
     * Ensures habit logs exist for [date] for the selected set.
     */
    suspend fun setSelectedHabitsForDate(date: String, habitIds: List<String>) {
        val existingDefs = definitionDao.getAllSync().map { it.id }.toSet()
        val cleaned = habitIds.filter { it in existingDefs }.distinct()
        configDao.insert(
            HabitDayConfig(
                date = date,
                habitIdsJson = json.encodeToString(cleaned),
                updatedAt = System.currentTimeMillis()
            )
        )
        configDao.deleteConfigsAfter(date)
        ensureLogsForDate(date, cleaned)
        // Drop logs for deselected habits on this date and future
        val currentLogs = logDao.getByDateSync(date)
        currentLogs.filter { it.habitId !in cleaned }.forEach {
            logDao.deleteForDateAndHabit(date, it.habitId)
        }
        val allDefs = definitionDao.getAllSync().map { it.id }
        allDefs.filter { it !in cleaned }.forEach { habitId ->
            logDao.deleteHabitAfterDate(date, habitId)
        }
    }

    suspend fun ensureLogsForDate(date: String, habitIds: List<String>? = null) {
        val ids = habitIds ?: getSelectedHabitIdsForDate(date)
        val existing = logDao.getByDateSync(date).associateBy { it.habitId }
        ids.forEach { habitId ->
            if (habitId !in existing) {
                logDao.insert(
                    HabitLog(
                        id = "hlog_${date}_$habitId",
                        date = date,
                        habitId = habitId,
                        status = HabitStatus.NONE
                    )
                )
            }
        }
    }

    suspend fun getLogsForDate(date: String): List<HabitLog> {
        ensureLogsForDate(date)
        val selected = getSelectedHabitIdsForDate(date)
        return logDao.getByDateSync(date).filter { it.habitId in selected }
    }

    fun observeLogsForDate(date: String): Flow<List<HabitLog>> = logDao.getByDate(date)

    suspend fun updateHabitStatus(date: String, habitId: String, status: HabitStatus) {
        val existing = logDao.getByDateSync(date).find { it.habitId == habitId }
        if (existing != null) {
            logDao.update(existing.copy(status = status, timestamp = System.currentTimeMillis()))
        } else {
            logDao.insert(
                HabitLog(
                    id = "hlog_${date}_$habitId",
                    date = date,
                    habitId = habitId,
                    status = status
                )
            )
        }
    }

    suspend fun getDatesWithAnyHabitData(startDate: String, endDate: String): Set<String> {
        return logDao.getBetweenSync(startDate, endDate)
            .filter { it.status != HabitStatus.NONE }
            .map { it.date }
            .toSet()
    }

    suspend fun getDatesWithLogs(): Set<String> = logDao.getDatesWithLogs().toSet()

    /**
     * Persists the in-memory Routine habit snapshot for [date]:
     * catalog definitions, day selection, and per-habit statuses.
     */
    suspend fun persistDaySnapshot(
        date: String,
        definitions: List<HabitDefinition>,
        selectedIds: List<String>,
        logs: List<HabitLog>,
    ) {
        val keepIds = definitions.map { it.id }.toSet()
        definitionDao.getAllSync()
            .filter { it.id !in keepIds }
            .forEach { deleteHabit(it.id) }
        definitions.forEach { definitionDao.insert(it) }

        val cleanedSelected = selectedIds.filter { it in keepIds }.distinct()
        setSelectedHabitsForDate(date, cleanedSelected)

        val logByHabit = logs.associateBy { it.habitId }
        cleanedSelected.forEach { habitId ->
            val status = logByHabit[habitId]?.status ?: HabitStatus.NONE
            updateHabitStatus(date, habitId, status)
        }
    }

    private fun decodeIds(raw: String): List<String> =
        try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
}
