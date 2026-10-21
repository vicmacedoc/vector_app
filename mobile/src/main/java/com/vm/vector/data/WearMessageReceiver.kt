package com.vm.vector.data

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityLabel
import com.vm.core.models.FoodEntry
import com.vm.core.models.MotionTask
import com.vm.core.models.SupermarketMath
import com.vm.core.models.VectorItem
import com.vm.core.models.effectiveEndTime
import com.vm.core.models.effectiveStartTime
import com.vm.core.wear.ActivitiesForWearPayload
import com.vm.core.wear.FoodDayForWearPayload
import com.vm.core.wear.MotionForWearPayload
import com.vm.core.wear.SupermarketForWearPayload
import com.vm.core.wear.WearActivityCompletion
import com.vm.core.wear.WearRoutineActivity
import com.vm.core.wear.WearSupermarketItem
import com.vm.core.wear.WearFoodItem
import com.vm.core.wear.WearFoodState
import com.vm.core.wear.WearMotionBucket
import com.vm.core.wear.WearMotionTask
import com.vm.core.wear.WearPaths
import com.vm.core.wear.WorkoutsForDatePayload
import com.vm.vector.VectorApplication
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Handles Wear Data Layer messages. Primary entry: [com.vm.vector.wear.VectorWearableListenerService].
 * [register] is kept as a no-op for compatibility; GMS delivers inbound messages via the service.
 */
object WearMessageReceiver {

    private const val TAG = "WearMessageReceiver"

    fun register(application: Application) {
        // Inbound messages are handled by [com.vm.vector.wear.VectorWearableListenerService].
    }

    fun onWorkoutCompletedFromWear(app: VectorApplication, data: ByteArray) {
        try {
            val payload = decodeWorkoutCompleted(data)
            app.addPendingWearData(payload.date, payload.sets)
            app.persistWearWorkoutCompletion(payload.date, payload.sets)
            app.setWearWorkoutReceivedMessage("Workout received from watch and saved")
            val sessionTitle = payload.sets.firstOrNull()?.sessionTitle?.takeIf { it.isNotBlank() }
                ?: "Workout"
            app.wearActivityStore.record(
                type = WearActivityEventType.WORKOUT,
                title = sessionTitle,
                date = payload.date,
            )
        } catch (e: Exception) {
            Log.e(TAG, "decode workout_completed failed", e)
        }
    }

    /** Routine wear sync removed (Habits + Activities). Kept as no-op for older watches. */
    fun onRoutineCompletedFromWear(app: VectorApplication, data: ByteArray) {
        Log.d(TAG, "Ignoring routine_completed from wear (${data.size} bytes)")
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

    /** Routine wear sync removed; reply with empty payload so older watches do not hang. */
    suspend fun replyRoutineRequest(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        Log.d(TAG, "Ignoring routine_request from wear")
        try {
            Wearable.getMessageClient(context)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.ROUTINE_RESPONSE, ByteArray(0))
                .await()
        } catch (_: Exception) {
        }
    }

    suspend fun replyMotionRequest(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        val payload = buildMotionPayload(app)
        try {
            Wearable.getMessageClient(context)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.MOTION_RESPONSE, encodeMotionResponse(payload))
                .await()
        } catch (_: Exception) {
        }
    }

    suspend fun onMotionComplete(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        try {
            val complete = decodeMotionComplete(messageEvent.data)
            app.brainRepository.completeTask(complete.taskId)
            val payload = buildMotionPayload(app)
            Wearable.getMessageClient(context)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.MOTION_RESPONSE, encodeMotionResponse(payload))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "motion_complete failed", e)
        }
    }

    suspend fun replyFoodRequest(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        val date = try {
            decodeFoodRequest(messageEvent.data)
        } catch (_: Exception) {
            return
        }
        val payload = buildFoodDayPayload(app, date)
        try {
            Wearable.getMessageClient(context)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.FOOD_RESPONSE, encodeFoodResponse(payload))
                .await()
        } catch (_: Exception) {
        }
    }

    suspend fun onFoodStatusSet(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        try {
            val set = decodeFoodStatusSet(messageEvent.data)
            val foodRepo = app.foodRepository
            var entries = foodRepo.getEntriesByDateSync(set.date)
            if (entries.isEmpty()) {
                val preset = foodRepo.loadPresetEntriesForDate(set.date).getOrNull().orEmpty()
                if (preset.isNotEmpty()) {
                    foodRepo.replaceEntriesForDate(set.date, preset)
                    entries = preset
                }
            }
            val updated = entries.map { entry ->
                if (entry.id != set.entryId) entry
                else when (set.state) {
                    WearFoodState.NONE -> entry.copy(isChecked = false, notEaten = false)
                    WearFoodState.EATEN -> entry.copy(isChecked = true, notEaten = false)
                    WearFoodState.SKIPPED -> entry.copy(isChecked = false, notEaten = true)
                }
            }
            if (updated.any { it.id == set.entryId }) {
                foodRepo.replaceEntriesForDate(set.date, updated)
                val patched = updated.first { it.id == set.entryId }
                app.emitWearFoodUpdated(set.date, patched)
            }
            val payload = buildFoodDayPayload(app, set.date)
            Wearable.getMessageClient(context)
                .sendMessage(messageEvent.sourceNodeId, WearPaths.FOOD_RESPONSE, encodeFoodResponse(payload))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "food_status_set failed", e)
        }
    }

    suspend fun replyActivitiesRequest(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        val date = try {
            decodeActivitiesRequest(messageEvent.data)
        } catch (_: Exception) {
            return
        }
        sendActivities(app, context, messageEvent.sourceNodeId, date)
    }

    suspend fun onActivityCompletionSet(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        try {
            val set = decodeActivityCompletionSet(messageEvent.data)
            val completion = ActivityCompletion.valueOf(set.completion.name)
            ActivityRepository(app.database).updateCompletion(set.activityId, completion)
            app.emitWearActivityUpdated(set.date, set.activityId, completion)
            sendActivities(app, context, messageEvent.sourceNodeId, set.date)
        } catch (e: Exception) {
            Log.e(TAG, "activity_completion_set failed", e)
        }
    }

    suspend fun replySupermarketRequest(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        sendSupermarket(app, context, messageEvent.sourceNodeId)
    }

    suspend fun onSupermarketToggle(app: VectorApplication, context: Context, messageEvent: MessageEvent) {
        try {
            val toggle = decodeSupermarketToggle(messageEvent.data)
            val repo = SupermarketRepository.create(app)
            val list = repo.loadLocal() ?: return
            val index = resolveItemIndex(list.items, toggle.index, toggle.title) ?: return
            val updated = list.copy(
                items = list.items.mapIndexed { i, item ->
                    if (i == index) item.copy(isChecked = toggle.isChecked) else item
                }
            )
            repo.save(updated, updateBaseline = false)
            app.emitWearSupermarketUpdated(index, toggle.title, toggle.isChecked)
            sendSupermarket(app, context, messageEvent.sourceNodeId)
        } catch (e: Exception) {
            Log.e(TAG, "supermarket_toggle failed", e)
        }
    }

    private fun resolveItemIndex(items: List<VectorItem>, index: Int, title: String): Int? {
        if (index in items.indices && items[index].title == title) return index
        return items.indexOfFirst { it.title == title }.takeIf { it >= 0 }
    }

    private suspend fun sendActivities(
        app: VectorApplication,
        context: Context,
        nodeId: String,
        date: String,
    ) {
        val payload = buildActivitiesPayload(app, date)
        try {
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, WearPaths.ACTIVITIES_RESPONSE, encodeActivitiesResponse(payload))
                .await()
        } catch (_: Exception) {
        }
    }

    private suspend fun sendSupermarket(app: VectorApplication, context: Context, nodeId: String) {
        val payload = buildSupermarketPayload(app)
        try {
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, WearPaths.SUPERMARKET_RESPONSE, encodeSupermarketResponse(payload))
                .await()
        } catch (_: Exception) {
        }
    }

    private suspend fun buildActivitiesPayload(app: VectorApplication, date: String): ActivitiesForWearPayload {
        val repo = ActivityRepository(app.database)
        val labels = repo.getAllLabels().associateBy { it.id }
        val activities = repo.getEntriesForDate(date).map { entry ->
            val label = labels[entry.labelId]
            WearRoutineActivity(
                id = entry.id,
                title = entry.title,
                start = entry.effectiveStartTime(),
                end = entry.effectiveEndTime(),
                colorArgb = label?.colorArgb ?: ActivityLabel.SLEEP_COLOR_ARGB,
                labelName = label?.name.orEmpty(),
                completion = WearActivityCompletion.valueOf(entry.completion.name),
            )
        }
        return ActivitiesForWearPayload(date = date, activities = sleepArcs(app, date) + activities)
    }

    /** Sleep on day D: `00:00 → wake` from night D−1 and `bedtime → 24:00` from night D (evening bedtimes only). */
    private suspend fun sleepArcs(app: VectorApplication, date: String): List<WearRoutineActivity> {
        val dao = app.database.sleepEntryDao()
        val arcs = mutableListOf<WearRoutineActivity>()
        previousDate(date)?.let { prev ->
            val wake = dao.getByDateSync(prev)?.let { it.actualWakeUp ?: it.plannedWakeUp }
            if (!wake.isNullOrBlank()) {
                arcs += WearRoutineActivity(
                    id = "sleep_am_$date",
                    title = "Sleep",
                    start = "00:00",
                    end = wake,
                    colorArgb = ActivityLabel.SLEEP_COLOR_ARGB,
                    labelName = ActivityLabel.SLEEP_LABEL_NAME,
                    isSleep = true,
                )
            }
        }
        val bed = dao.getByDateSync(date)?.let { it.actualBedtime ?: it.plannedBedtime }
        val bedHour = bed?.substringBefore(":")?.toIntOrNull()
        if (bed != null && bedHour != null && bedHour >= 12) {
            arcs += WearRoutineActivity(
                id = "sleep_pm_$date",
                title = "Sleep",
                start = bed,
                end = "00:00",
                colorArgb = ActivityLabel.SLEEP_COLOR_ARGB,
                labelName = ActivityLabel.SLEEP_LABEL_NAME,
                isSleep = true,
            )
        }
        return arcs
    }

    private fun previousDate(date: String): String? = try {
        LocalDate.parse(date).minusDays(1).toString()
    } catch (_: Exception) {
        null
    }

    /** Sends what's left to buy plus anything already in the cart, in aisle/route order. */
    private suspend fun buildSupermarketPayload(app: VectorApplication): SupermarketForWearPayload {
        val list = SupermarketRepository.create(app).loadLocal()
            ?: return SupermarketForWearPayload(available = false)
        val indexOf = list.items.withIndex().associate { it.value.id to it.index }
        val items = SupermarketMath.groupedByAisle(list).flatMap { (aisle, aisleItems) ->
            aisleItems
                .filter { it.isChecked || SupermarketMath.needsBuy(it) }
                .mapNotNull { item ->
                    val index = indexOf[item.id] ?: return@mapNotNull null
                    WearSupermarketItem(
                        index = index,
                        title = item.title,
                        aisle = aisle,
                        isChecked = item.isChecked,
                        urgency = SupermarketMath.urgency(item).name,
                        remaining = item.remaining,
                        quantity = item.quantity,
                        unit = item.unit,
                    )
                }
        }
        return SupermarketForWearPayload(
            listName = list.name.ifBlank { DriveConfig.SUPERMARKET_FILENAME.removeSuffix(".json") },
            available = true,
            items = items,
        )
    }

    private suspend fun buildMotionPayload(app: VectorApplication): MotionForWearPayload {
        val tasks = app.database.motionTaskDao().getOpenSync()
        val (dayStart, dayEnd) = todayBounds()
        val wearTasks = tasks.map { task ->
            WearMotionTask(
                id = task.id,
                title = task.title,
                priority = task.priority.name,
                dueAt = task.dueAt,
                bucket = bucketFor(task, dayStart, dayEnd),
            )
        }.sortedWith(
            compareBy<WearMotionTask> { it.bucket.ordinal }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
        )
        return MotionForWearPayload(tasks = wearTasks)
    }

    private fun bucketFor(task: MotionTask, dayStart: Long, dayEnd: Long): WearMotionBucket {
        val due = task.dueAt ?: return WearMotionBucket.SOMEDAY
        return when {
            due < dayStart -> WearMotionBucket.OVERDUE
            due < dayEnd -> WearMotionBucket.TODAY
            else -> WearMotionBucket.UPCOMING
        }
    }

    private fun todayBounds(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return start to (start + TimeUnit.DAYS.toMillis(1))
    }

    private suspend fun buildFoodDayPayload(app: VectorApplication, date: String): FoodDayForWearPayload {
        val foodRepo = app.foodRepository
        var entries = foodRepo.getEntriesByDateSync(date)
        var hasPreset = entries.isNotEmpty()
        if (entries.isEmpty()) {
            val presetResult = foodRepo.loadPresetEntriesForDate(date)
            entries = presetResult.getOrNull().orEmpty()
            hasPreset = presetResult.isSuccess
        }
        return FoodDayForWearPayload(
            date = date,
            items = entries.map { it.toWearItem() },
            hasPreset = hasPreset || entries.isNotEmpty(),
        )
    }

    private fun FoodEntry.toWearItem(): WearFoodItem {
        val state = when {
            notEaten -> WearFoodState.SKIPPED
            isChecked -> WearFoodState.EATEN
            else -> WearFoodState.NONE
        }
        return WearFoodItem(
            id = id,
            name = name,
            plannedTime = plannedTime ?: time,
            amount = amount ?: plannedAmount,
            unit = unit,
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fats = fats,
            state = state,
        )
    }
}
