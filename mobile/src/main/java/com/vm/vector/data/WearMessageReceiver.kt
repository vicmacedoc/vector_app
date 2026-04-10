package com.vm.vector.data

import android.app.Application
import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.vm.core.models.RoutineEntry
import com.vm.core.wear.RoutineForDatePayload
import com.vm.core.wear.RoutineWearEntry
import com.vm.core.wear.WearPaths
import com.vm.core.wear.WorkoutsForDatePayload
import com.vm.vector.VectorApplication
import kotlinx.coroutines.tasks.await

/**
 * Handles Wear Data Layer messages. Primary entry: [VectorWearableListenerService].
 * [register] is kept as a no-op for compatibility; GMS delivers inbound messages via the service.
 */
object WearMessageReceiver {

    fun register(application: Application) {
        // Inbound messages are handled by [com.vm.vector.wear.VectorWearableListenerService].
    }

    fun onWorkoutCompletedFromWear(app: VectorApplication, data: ByteArray) {
        try {
            val payload = decodeWorkoutCompleted(data)
            app.addPendingWearData(payload.date, payload.sets)
            app.persistWearWorkoutCompletion(payload.date, payload.sets)
            app.setWearWorkoutReceivedMessage("Workout received from watch and saved")
        } catch (_: Exception) {
        }
    }

    fun onRoutineCompletedFromWear(app: VectorApplication, data: ByteArray) {
        try {
            val payload = decodeRoutineCompleted(data)
            app.addPendingRoutineWearData(payload.date, payload.entryId, payload.currentValue)
            app.setWearRoutineReceivedMessage("Routine data received from watch")
        } catch (_: Exception) {
        }
    }

    suspend fun replyWorkoutsRequest(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        val date = try {
            decodeWorkoutsRequest(messageEvent.data)
        } catch (_: Exception) {
            return
        }
        val sets = app.getWorkoutSetsForWearRequest(date)
            ?: app.workoutRepository.loadWorkoutForDateDisplay(date).getOrNull()
            ?: emptyList()
        val payload = WorkoutsForDatePayload(date = date, sets = sets)
        val responseBytes = encodeWorkoutsResponse(payload)
        try {
            Wearable.getMessageClient(context)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.WORKOUTS_RESPONSE, responseBytes)
                .await()
        } catch (_: Exception) {
        }
    }

    suspend fun replyRoutineRequest(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        val date = try {
            messageEvent.data.decodeToString()
        } catch (_: Exception) {
            return
        }
        val rows = app.routineRepository.loadRoutineEntriesForWearSync(date)
        val wearEntries = rows.map { it.toRoutineWearEntry() }
        val payload = RoutineForDatePayload(date = date, entries = wearEntries)
        val responseBytes = encodeRoutineResponse(payload)
        try {
            Wearable.getMessageClient(context)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.ROUTINE_RESPONSE, responseBytes)
                .await()
        } catch (_: Exception) {
        }
    }

    private fun RoutineEntry.toRoutineWearEntry(): RoutineWearEntry {
        val u = unit ?: "h"
        return RoutineWearEntry(
            id = id,
            title = title,
            category = category,
            unit = u,
            goalValue = goalValue ?: 0.0,
            partialThreshold = partialThreshold ?: 0.0,
            currentValue = currentValue,
            directionBetter = directionBetter,
            minValue = minValue,
            maxValue = maxValue
        )
    }
}
