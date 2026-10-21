package com.vm.vector.wear

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.vm.core.wear.WearPaths
import com.vm.vector.VectorApplication
import com.vm.vector.data.WearAudioReceiver
import com.vm.vector.data.WearMessageReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Delivers Wear Data Layer messages when the app is not in the foreground.
 * [com.google.android.gms.wearable.MessageClient.addListener] on [android.app.Application] alone
 * is often unreliable for phone ← watch; this service is the supported path.
 */
class VectorWearableListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val app = application as? VectorApplication ?: return
        when (messageEvent.path) {
            WearPaths.WORKOUT_COMPLETED ->
                WearMessageReceiver.onWorkoutCompletedFromWear(app, messageEvent.data)
            WearPaths.ROUTINE_COMPLETED ->
                WearMessageReceiver.onRoutineCompletedFromWear(app, messageEvent.data)
            WearPaths.WORKOUTS_REQUEST ->
                scope.launch {
                    WearMessageReceiver.replyWorkoutsRequest(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.ROUTINE_REQUEST ->
                scope.launch {
                    WearMessageReceiver.replyRoutineRequest(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.MOTION_REQUEST ->
                scope.launch {
                    WearMessageReceiver.replyMotionRequest(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.MOTION_COMPLETE ->
                scope.launch {
                    WearMessageReceiver.onMotionComplete(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.FOOD_REQUEST ->
                scope.launch {
                    WearMessageReceiver.replyFoodRequest(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.FOOD_STATUS_SET ->
                scope.launch {
                    WearMessageReceiver.onFoodStatusSet(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.ACTIVITIES_REQUEST ->
                scope.launch {
                    WearMessageReceiver.replyActivitiesRequest(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.ACTIVITY_COMPLETION_SET ->
                scope.launch {
                    WearMessageReceiver.onActivityCompletionSet(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.SUPERMARKET_REQUEST ->
                scope.launch {
                    WearMessageReceiver.replySupermarketRequest(app, this@VectorWearableListenerService, messageEvent)
                }
            WearPaths.SUPERMARKET_TOGGLE ->
                scope.launch {
                    WearMessageReceiver.onSupermarketToggle(app, this@VectorWearableListenerService, messageEvent)
                }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val app = application as? VectorApplication ?: return
        if (!channel.path.startsWith(WearPaths.AUDIO_CHANNEL_PREFIX)) return
        scope.launch {
            WearAudioReceiver.onChannelOpened(app, channel)
        }
    }
}
