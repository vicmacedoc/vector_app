package com.vm.vector.data

import android.app.Application
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.vm.core.wear.WearPaths
import com.vm.core.wear.WorkoutsForDatePayload
import com.vm.vector.VectorApplication
import com.vm.vector.data.decodeWorkoutCompleted
import com.vm.vector.data.decodeWorkoutsRequest
import com.vm.vector.data.encodeWorkoutsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives messages from the Wear app: workouts request (reply with workout list)
 * and workout completed (merge into pending wear data for Calendar).
 */
object WearMessageReceiver {

    private var listener: MessageClient.OnMessageReceivedListener? = null

    fun register(application: Application) {
        if (listener != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            when (messageEvent.path) {
                WearPaths.WORKOUTS_REQUEST -> {
                    scope.launch(Dispatchers.IO) {
                        handleWorkoutsRequest(application, messageEvent)
                    }
                }
                WearPaths.WORKOUT_COMPLETED -> {
                    scope.launch(Dispatchers.IO) {
                        handleWorkoutCompleted(application, messageEvent)
                    }
                }
            }
        }
        Wearable.getMessageClient(application).addListener(listener!!)
    }

    private suspend fun handleWorkoutsRequest(application: Application, messageEvent: MessageEvent) {
        val app = application as? VectorApplication ?: return
        val date = try {
            decodeWorkoutsRequest(messageEvent.data)
        } catch (e: Exception) {
            return
        }
        val sets = app.getWorkoutSetsForWearRequest(date)
            ?: app.workoutRepository.loadWorkoutForDateDisplay(date).getOrNull()
            ?: emptyList()
        val payload = WorkoutsForDatePayload(date = date, sets = sets)
        val responseBytes = encodeWorkoutsResponse(payload)
        try {
            Wearable.getMessageClient(application)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.WORKOUTS_RESPONSE, responseBytes)
                .await()
        } catch (_: Exception) {
            // Ignore send failures (watch may have disconnected)
        }
    }

    private fun handleWorkoutCompleted(application: Application, messageEvent: MessageEvent) {
        val app = application as? VectorApplication ?: return
        try {
            val payload = decodeWorkoutCompleted(messageEvent.data)
            app.addPendingWearData(payload.date, payload.sets)
            app.setWearWorkoutReceivedMessage("Workout data received from watch")
        } catch (_: Exception) {
            // Ignore parse errors
        }
    }
}
