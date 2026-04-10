package com.vm.vector.data

import com.vm.core.wear.RoutineCompletedPayload
import com.vm.core.wear.RoutineForDatePayload
import com.vm.core.wear.WorkoutCompletedPayload
import com.vm.core.wear.WorkoutsForDatePayload
import kotlinx.serialization.json.Json

private fun createWearJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Decode request payload from Wear: date string. */
fun decodeWorkoutsRequest(payload: ByteArray): String = payload.decodeToString()

/** Encode response payload (Mobile -> Wear). */
fun encodeWorkoutsResponse(payload: WorkoutsForDatePayload): ByteArray =
    createWearJson().encodeToString(WorkoutsForDatePayload.serializer(), payload).encodeToByteArray()

/** Decode completed workout payload (Wear -> Mobile). */
fun decodeWorkoutCompleted(payload: ByteArray): WorkoutCompletedPayload =
    createWearJson().decodeFromString(WorkoutCompletedPayload.serializer(), payload.decodeToString())

fun encodeRoutineResponse(payload: RoutineForDatePayload): ByteArray =
    createWearJson().encodeToString(RoutineForDatePayload.serializer(), payload).encodeToByteArray()

fun decodeRoutineCompleted(payload: ByteArray): RoutineCompletedPayload =
    createWearJson().decodeFromString(RoutineCompletedPayload.serializer(), payload.decodeToString())
