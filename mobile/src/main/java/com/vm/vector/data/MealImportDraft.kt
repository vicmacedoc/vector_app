package com.vm.vector.data

import com.vm.core.models.AuditStatus
import com.vm.core.models.FoodEntry
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable

/** A meal row before it reaches the Food calendar (from clipboard, AI or the library). */
data class MealImportDraft(
    val name: String = "",
    /** HH:mm; blank means "use the phone clock". */
    val time: String = "",
    val amount: Double = 0.0,
    val unit: String = "g",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    /** True stores time/amount as the plan baseline so the meal is PLANNED. */
    val asPlanned: Boolean = false,
)

/** Clipboard shape for one meal; `plannedTime` / `plannedAmount` are accepted as aliases. */
@Serializable
private data class MealClipboardJson(
    val name: String = "",
    val time: String? = null,
    val plannedTime: String? = null,
    val amount: Double? = null,
    val plannedAmount: Double? = null,
    val unit: String = "g",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
)

/**
 * Parses clipboard meals for [mode]: one object for [CalendarAiMode.SINGLE], otherwise an array
 * or `{ "meals": [...] }`. Each meal needs a name and an amount above 0; time is optional.
 */
fun parseMealClipboardText(text: String, mode: CalendarAiMode): Result<List<MealImportDraft>> =
    clipboardItemsFor(text, mode, wrapperKey = "meals", itemNoun = "meal").mapCatching { objects ->
        objects.mapIndexed { index, obj ->
            val prefix = if (objects.size == 1) "Meal" else "Meal ${index + 1}"
            val item = calendarClipboardJson.decodeFromJsonElement(MealClipboardJson.serializer(), obj)
            require(item.name.isNotBlank()) { "$prefix is missing a name" }
            val amount = item.amount ?: item.plannedAmount
            require(amount != null && amount > 0) { "$prefix needs an amount greater than 0" }
            require(item.kcal >= 0) { "$prefix has negative kcal" }
            MealImportDraft(
                name = item.name.trim(),
                time = normalizeMealTimeOrBlank(item.time ?: item.plannedTime),
                amount = amount,
                unit = item.unit.ifBlank { "g" },
                kcal = item.kcal,
                protein = item.protein.coerceAtLeast(0.0),
                carbs = item.carbs.coerceAtLeast(0.0),
                fats = item.fats.coerceAtLeast(0.0),
            )
        }
    }

/** "7:5" → "07:05"; invalid or missing → "". */
fun normalizeMealTimeOrBlank(raw: String?): String {
    val parts = raw?.trim()?.split(":") ?: return ""
    if (parts.size < 2) return ""
    val h = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return ""
    val m = parts[1].take(2).toIntOrNull()?.takeIf { it in 0..59 } ?: return ""
    return String.format(Locale.US, "%02d:%02d", h, m)
}

fun MealImportDraft.validationError(): String? = when {
    name.isBlank() -> "Name is required"
    normalizeMealTimeOrBlank(time).isBlank() -> "Time must be HH:mm"
    amount <= 0 -> "Amount must be greater than 0"
    kcal < 0 -> "Calories can't be negative"
    else -> null
}

/** In-memory Food row for [date]; planned meals carry their own time/amount as the baseline. */
fun MealImportDraft.toFoodEntry(date: String): FoodEntry {
    val normalizedTime = normalizeMealTimeOrBlank(time).ifBlank { "12:00" }
    return FoodEntry(
        id = UUID.randomUUID().toString(),
        date = date,
        plannedTime = if (asPlanned) normalizedTime else null,
        name = name.trim(),
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fats = fats,
        plannedAmount = if (asPlanned) amount else null,
        time = normalizedTime,
        amount = amount,
        quantityMultiplier = 1.0,
        unit = unit.ifBlank { "g" },
        isChecked = false,
        notEaten = false,
        status = if (asPlanned) AuditStatus.PLANNED else AuditStatus.UNPLANNED,
        timestamp = System.currentTimeMillis(),
    )
}

fun GeminiNutritionClient.MealEstimate.toImportDraft(asPlanned: Boolean): MealImportDraft = MealImportDraft(
    name = name,
    time = normalizeMealTimeOrBlank(time),
    amount = amount,
    unit = unit,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fats = fats,
    asPlanned = asPlanned,
)
