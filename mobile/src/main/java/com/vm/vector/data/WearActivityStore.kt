package com.vm.vector.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
enum class WearActivityEventType {
    AUDIO,
    WORKOUT
}

@Serializable
data class WearActivityEvent(
    val id: String,
    val type: WearActivityEventType,
    val title: String,
    val timestamp: Long,
    val noteId: String? = null,
    val date: String? = null,
    val seen: Boolean = false,
)

/**
 * Prefs-backed recent activity from the watch (voice notes, workouts) for the Home card.
 * Keeps the last [MAX_EVENTS] events from the past [MAX_AGE_MS].
 */
class WearActivityStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<WearActivityEvent>> = _events.asStateFlow()

    fun unseen(): List<WearActivityEvent> = _events.value.filter { !it.seen }

    fun record(
        type: WearActivityEventType,
        title: String,
        noteId: String? = null,
        date: String? = null,
    ) {
        val event = WearActivityEvent(
            id = "wear_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            type = type,
            title = title,
            timestamp = System.currentTimeMillis(),
            noteId = noteId,
            date = date,
            seen = false,
        )
        val pruned = (listOf(event) + _events.value)
            .filter { System.currentTimeMillis() - it.timestamp <= MAX_AGE_MS }
            .take(MAX_EVENTS)
        persist(pruned)
    }

    fun markAllSeen() {
        persist(_events.value.map { it.copy(seen = true) })
    }

    fun markSeen(id: String) {
        persist(_events.value.map { if (it.id == id) it.copy(seen = true) else it })
    }

    private fun load(): List<WearActivityEvent> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString(WearActivityList.serializer(), raw).events
                .filter { System.currentTimeMillis() - it.timestamp <= MAX_AGE_MS }
                .take(MAX_EVENTS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(events: List<WearActivityEvent>) {
        _events.value = events
        prefs.edit()
            .putString(KEY, json.encodeToString(WearActivityList.serializer(), WearActivityList(events)))
            .apply()
    }

    companion object {
        private const val PREFS = "wear_activity"
        private const val KEY = "events"
        private const val MAX_EVENTS = 10
        private val MAX_AGE_MS = TimeUnit.HOURS.toMillis(48)
    }
}

@Serializable
private data class WearActivityList(val events: List<WearActivityEvent> = emptyList())
