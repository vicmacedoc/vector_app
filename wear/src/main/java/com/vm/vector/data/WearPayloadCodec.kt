package com.vm.vector.data

import com.vm.core.wear.WorkoutCompletedPayload
import com.vm.core.wear.WorkoutsForDatePayload
import kotlinx.serialization.json.Json

private fun createWearJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun encodeWorkoutsRequest(date: String): ByteArray = date.encodeToByteArray()

fun decodeWorkoutsResponse(payload: ByteArray): WorkoutsForDatePayload =
    createWearJson().decodeFromString(WorkoutsForDatePayload.serializer(), payload.decodeToString())

fun encodeWorkoutCompleted(payload: WorkoutCompletedPayload): ByteArray =
    createWearJson().encodeToString(WorkoutCompletedPayload.serializer(), payload).encodeToByteArray()
