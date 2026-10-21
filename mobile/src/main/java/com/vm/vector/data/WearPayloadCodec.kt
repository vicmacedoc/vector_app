package com.vm.vector.data

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

fun encodeMotionResponse(payload: MotionForWearPayload): ByteArray =
    createWearJson().encodeToString(MotionForWearPayload.serializer(), payload).encodeToByteArray()

fun decodeMotionComplete(payload: ByteArray): MotionCompletePayload =
    createWearJson().decodeFromString(MotionCompletePayload.serializer(), payload.decodeToString())

fun decodeFoodRequest(payload: ByteArray): String = payload.decodeToString()

fun encodeFoodResponse(payload: FoodDayForWearPayload): ByteArray =
    createWearJson().encodeToString(FoodDayForWearPayload.serializer(), payload).encodeToByteArray()

fun decodeFoodStatusSet(payload: ByteArray): FoodStatusSetPayload =
    createWearJson().decodeFromString(FoodStatusSetPayload.serializer(), payload.decodeToString())

fun decodeActivitiesRequest(payload: ByteArray): String = payload.decodeToString()

fun encodeActivitiesResponse(payload: ActivitiesForWearPayload): ByteArray =
    createWearJson().encodeToString(ActivitiesForWearPayload.serializer(), payload).encodeToByteArray()

fun decodeActivityCompletionSet(payload: ByteArray): ActivityCompletionSetPayload =
    createWearJson().decodeFromString(ActivityCompletionSetPayload.serializer(), payload.decodeToString())

fun encodeSupermarketResponse(payload: SupermarketForWearPayload): ByteArray =
    createWearJson().encodeToString(SupermarketForWearPayload.serializer(), payload).encodeToByteArray()

fun decodeSupermarketToggle(payload: ByteArray): SupermarketTogglePayload =
    createWearJson().decodeFromString(SupermarketTogglePayload.serializer(), payload.decodeToString())
