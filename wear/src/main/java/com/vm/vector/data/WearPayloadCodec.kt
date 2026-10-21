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

fun encodeWorkoutsRequest(date: String): ByteArray = date.encodeToByteArray()

fun decodeWorkoutsResponse(payload: ByteArray): WorkoutsForDatePayload =
    createWearJson().decodeFromString(WorkoutsForDatePayload.serializer(), payload.decodeToString())

fun encodeWorkoutCompleted(payload: WorkoutCompletedPayload): ByteArray =
    createWearJson().encodeToString(WorkoutCompletedPayload.serializer(), payload).encodeToByteArray()

fun encodeRoutineRequest(date: String): ByteArray = date.encodeToByteArray()

fun decodeRoutineResponse(payload: ByteArray): RoutineForDatePayload =
    createWearJson().decodeFromString(RoutineForDatePayload.serializer(), payload.decodeToString())

fun encodeRoutineCompleted(payload: RoutineCompletedPayload): ByteArray =
    createWearJson().encodeToString(RoutineCompletedPayload.serializer(), payload).encodeToByteArray()

fun encodeMotionRequest(): ByteArray = ByteArray(0)

fun decodeMotionResponse(payload: ByteArray): MotionForWearPayload =
    createWearJson().decodeFromString(MotionForWearPayload.serializer(), payload.decodeToString())

fun encodeMotionComplete(payload: MotionCompletePayload): ByteArray =
    createWearJson().encodeToString(MotionCompletePayload.serializer(), payload).encodeToByteArray()

fun encodeFoodRequest(date: String): ByteArray = date.encodeToByteArray()

fun decodeFoodResponse(payload: ByteArray): FoodDayForWearPayload =
    createWearJson().decodeFromString(FoodDayForWearPayload.serializer(), payload.decodeToString())

fun encodeFoodStatusSet(payload: FoodStatusSetPayload): ByteArray =
    createWearJson().encodeToString(FoodStatusSetPayload.serializer(), payload).encodeToByteArray()

fun encodeActivitiesRequest(date: String): ByteArray = date.encodeToByteArray()

fun decodeActivitiesResponse(payload: ByteArray): ActivitiesForWearPayload =
    createWearJson().decodeFromString(ActivitiesForWearPayload.serializer(), payload.decodeToString())

fun encodeActivityCompletionSet(payload: ActivityCompletionSetPayload): ByteArray =
    createWearJson().encodeToString(ActivityCompletionSetPayload.serializer(), payload).encodeToByteArray()

fun encodeSupermarketRequest(): ByteArray = ByteArray(0)

fun decodeSupermarketResponse(payload: ByteArray): SupermarketForWearPayload =
    createWearJson().decodeFromString(SupermarketForWearPayload.serializer(), payload.decodeToString())

fun encodeSupermarketToggle(payload: SupermarketTogglePayload): ByteArray =
    createWearJson().encodeToString(SupermarketTogglePayload.serializer(), payload).encodeToByteArray()
