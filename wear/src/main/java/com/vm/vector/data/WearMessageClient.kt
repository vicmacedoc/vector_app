package com.vm.vector.data

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.vm.core.wear.RoutineForDatePayload
import com.vm.core.wear.RoutineCompletedPayload
import com.vm.core.wear.WearPaths
import com.vm.core.wear.WorkoutCompletedPayload
import com.vm.core.wear.WorkoutsForDatePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sends and receives messages between the Wear app and the Mobile app.
 */
class WearMessageClient(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messageClient: MessageClient get() = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient get() = Wearable.getNodeClient(context)

    private var responseListener: MessageClient.OnMessageReceivedListener? = null

    /**
     * Callback when workouts for date response is received.
     */
    var onWorkoutsResponse: ((WorkoutsForDatePayload) -> Unit)? = null

    var onRoutineResponse: ((RoutineForDatePayload) -> Unit)? = null

    /**
     * Register to receive WORKOUTS_RESPONSE. Call before requestWorkoutsForDate.
     */
    fun addListener() {
        if (responseListener != null) return
        responseListener = MessageClient.OnMessageReceivedListener { event ->
            when (event.path) {
                WearPaths.WORKOUTS_RESPONSE -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val payload = decodeWorkoutsResponse(event.data)
                            onWorkoutsResponse?.invoke(payload)
                        } catch (_: Exception) { }
                    }
                }
                WearPaths.ROUTINE_RESPONSE -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val payload = decodeRoutineResponse(event.data)
                            onRoutineResponse?.invoke(payload)
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        messageClient.addListener(responseListener!!)
    }

    fun removeListener() {
        responseListener?.let { messageClient.removeListener(it) }
        responseListener = null
    }

    /**
     * Send workouts request for the given date. Expect WORKOUTS_RESPONSE via onWorkoutsResponse.
     */
    suspend fun requestWorkoutsForDate(date: String): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        val localNode = nodeClient.localNode.await()
        val remoteNodes = nodes.filter { it.id != localNode?.id }
        if (remoteNodes.isEmpty()) return false
        val payload = encodeWorkoutsRequest(date)
        for (node in remoteNodes) {
            try {
                messageClient.sendMessage(node.id, WearPaths.WORKOUTS_REQUEST, payload).await()
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    /**
     * Send completed workout payload to the phone.
     */
    suspend fun sendWorkoutCompleted(payload: WorkoutCompletedPayload): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        val localNode = nodeClient.localNode.await()
        val remoteNodes = nodes.filter { it.id != localNode?.id }
        if (remoteNodes.isEmpty()) return false
        val bytes = encodeWorkoutCompleted(payload)
        for (node in remoteNodes) {
            try {
                messageClient.sendMessage(node.id, WearPaths.WORKOUT_COMPLETED, bytes).await()
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    suspend fun requestRoutineForDate(date: String): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        val localNode = nodeClient.localNode.await()
        val remoteNodes = nodes.filter { it.id != localNode?.id }
        if (remoteNodes.isEmpty()) return false
        val payload = encodeRoutineRequest(date)
        for (node in remoteNodes) {
            try {
                messageClient.sendMessage(node.id, WearPaths.ROUTINE_REQUEST, payload).await()
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    suspend fun sendRoutineCompleted(payload: RoutineCompletedPayload): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        val localNode = nodeClient.localNode.await()
        val remoteNodes = nodes.filter { it.id != localNode?.id }
        if (remoteNodes.isEmpty()) return false
        val bytes = encodeRoutineCompleted(payload)
        for (node in remoteNodes) {
            try {
                messageClient.sendMessage(node.id, WearPaths.ROUTINE_COMPLETED, bytes).await()
                return true
            } catch (_: Exception) { }
        }
        return false
    }
}
