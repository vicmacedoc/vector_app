package com.vm.vector.data

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.vm.core.wear.ActivitiesForWearPayload
import com.vm.core.wear.ActivityCompletionSetPayload
import com.vm.core.wear.FoodDayForWearPayload
import com.vm.core.wear.SupermarketForWearPayload
import com.vm.core.wear.SupermarketTogglePayload
import com.vm.core.wear.FoodStatusSetPayload
import com.vm.core.wear.MotionCompletePayload
import com.vm.core.wear.MotionForWearPayload
import com.vm.core.wear.RoutineCompletedPayload
import com.vm.core.wear.RoutineForDatePayload
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

    var onWorkoutsResponse: ((WorkoutsForDatePayload) -> Unit)? = null
    var onRoutineResponse: ((RoutineForDatePayload) -> Unit)? = null
    var onMotionResponse: ((MotionForWearPayload) -> Unit)? = null
    var onFoodResponse: ((FoodDayForWearPayload) -> Unit)? = null
    var onActivitiesResponse: ((ActivitiesForWearPayload) -> Unit)? = null
    var onSupermarketResponse: ((SupermarketForWearPayload) -> Unit)? = null

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
                WearPaths.MOTION_RESPONSE -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val payload = decodeMotionResponse(event.data)
                            onMotionResponse?.invoke(payload)
                        } catch (_: Exception) { }
                    }
                }
                WearPaths.FOOD_RESPONSE -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val payload = decodeFoodResponse(event.data)
                            onFoodResponse?.invoke(payload)
                        } catch (_: Exception) { }
                    }
                }
                WearPaths.ACTIVITIES_RESPONSE -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val payload = decodeActivitiesResponse(event.data)
                            onActivitiesResponse?.invoke(payload)
                        } catch (_: Exception) { }
                    }
                }
                WearPaths.SUPERMARKET_RESPONSE -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val payload = decodeSupermarketResponse(event.data)
                            onSupermarketResponse?.invoke(payload)
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

    private suspend fun sendToPhone(path: String, payload: ByteArray): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        val localNode = nodeClient.localNode.await()
        val remoteNodes = nodes.filter { it.id != localNode?.id }
        if (remoteNodes.isEmpty()) return false
        for (node in remoteNodes) {
            try {
                messageClient.sendMessage(node.id, path, payload).await()
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    suspend fun requestWorkoutsForDate(date: String): Boolean =
        sendToPhone(WearPaths.WORKOUTS_REQUEST, encodeWorkoutsRequest(date))

    suspend fun sendWorkoutCompleted(payload: WorkoutCompletedPayload): Boolean =
        sendToPhone(WearPaths.WORKOUT_COMPLETED, encodeWorkoutCompleted(payload))

    suspend fun requestRoutineForDate(date: String): Boolean =
        sendToPhone(WearPaths.ROUTINE_REQUEST, encodeRoutineRequest(date))

    suspend fun sendRoutineCompleted(payload: RoutineCompletedPayload): Boolean =
        sendToPhone(WearPaths.ROUTINE_COMPLETED, encodeRoutineCompleted(payload))

    suspend fun requestMotion(): Boolean =
        sendToPhone(WearPaths.MOTION_REQUEST, encodeMotionRequest())

    suspend fun sendMotionComplete(payload: MotionCompletePayload): Boolean =
        sendToPhone(WearPaths.MOTION_COMPLETE, encodeMotionComplete(payload))

    suspend fun requestFoodForDate(date: String): Boolean =
        sendToPhone(WearPaths.FOOD_REQUEST, encodeFoodRequest(date))

    suspend fun sendFoodStatusSet(payload: FoodStatusSetPayload): Boolean =
        sendToPhone(WearPaths.FOOD_STATUS_SET, encodeFoodStatusSet(payload))

    suspend fun requestActivitiesForDate(date: String): Boolean =
        sendToPhone(WearPaths.ACTIVITIES_REQUEST, encodeActivitiesRequest(date))

    suspend fun sendActivityCompletionSet(payload: ActivityCompletionSetPayload): Boolean =
        sendToPhone(WearPaths.ACTIVITY_COMPLETION_SET, encodeActivityCompletionSet(payload))

    suspend fun requestSupermarket(): Boolean =
        sendToPhone(WearPaths.SUPERMARKET_REQUEST, encodeSupermarketRequest())

    suspend fun sendSupermarketToggle(payload: SupermarketTogglePayload): Boolean =
        sendToPhone(WearPaths.SUPERMARKET_TOGGLE, encodeSupermarketToggle(payload))

    suspend fun hasConnectedPhone(): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        val localNode = nodeClient.localNode.await()
        return nodes.any { it.id != localNode?.id }
    }
}
