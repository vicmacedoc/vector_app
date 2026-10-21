package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class AuditStatus { PLANNED, ADJUSTED, UNPLANNED }

@Entity(
    tableName = "food_logs",
    indices = [Index(value = ["date"], name = "index_food_logs_date")]
)
@Serializable
data class FoodEntry(
    @PrimaryKey val id: String,
    val date: String,             // ISO 8601: YYYY-MM-DD
    /** Preset baseline time; null for manually / AI-added meals. */
    val plannedTime: String? = null,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    /** Preset baseline amount; null for manually / AI-added meals. */
    val plannedAmount: Double? = null,
    /** Effective meal time (editable); grouping uses this preferentially. */
    val time: String? = null,
    /** Effective amount (editable); quantity slider scales against this base. */
    val amount: Double? = null,
    val quantityMultiplier: Double = 1.0,
    val unit: String,
    val isChecked: Boolean = false,
    /** When true, meal was not eaten (red tile); takes precedence over isChecked for display. */
    val notEaten: Boolean = false,
    val status: AuditStatus = AuditStatus.PLANNED,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun effectiveTime(): String =
        time?.takeIf { it.isNotBlank() }
            ?: plannedTime?.takeIf { it.isNotBlank() }
            ?: "12:00"

    fun effectiveAmount(): Double = amount ?: plannedAmount ?: 0.0

    fun hasPresetBaseline(): Boolean = plannedTime != null || plannedAmount != null

    fun currentKcal(): Double = kcal * quantityMultiplier
    fun currentProtein(): Double = protein * quantityMultiplier
    fun currentCarbs(): Double = carbs * quantityMultiplier
    fun currentFats(): Double = fats * quantityMultiplier

    /** Planned baseline kcal, independent of the quantity slider. 0 if this meal is not on the plan. */
    fun plannedKcal(): Double = plannedStoredNutrient(kcal)
    fun plannedProtein(): Double = plannedStoredNutrient(protein)
    fun plannedCarbs(): Double = plannedStoredNutrient(carbs)
    fun plannedFats(): Double = plannedStoredNutrient(fats)

    /**
     * Stored macros are relative to [amount]; scale them back to [plannedAmount] when the two diverge
     * (slider or baked adjustment). Unplanned meals contribute nothing to the day's plan.
     */
    private fun plannedStoredNutrient(stored: Double): Double {
        if (!hasPresetBaseline()) return 0.0
        val plannedAmt = plannedAmount ?: return stored
        val baseAmt = effectiveAmount()
        if (baseAmt == 0.0) return stored
        return stored * (plannedAmt / baseAmt)
    }

    /**
     * Resolves audit status from baseline vs effective values and the current multiplier.
     * Manual / AI meals (no planned baseline) stay [AuditStatus.UNPLANNED].
     */
    fun resolveAuditStatus(multiplier: Double = quantityMultiplier): AuditStatus {
        if (!hasPresetBaseline()) return AuditStatus.UNPLANNED
        val timeDiffers = plannedTime != null && effectiveTime() != plannedTime
        val amountDiffers = plannedAmount != null && effectiveAmount() != plannedAmount
        return if (multiplier != 1.0 || timeDiffers || amountDiffers) {
            AuditStatus.ADJUSTED
        } else {
            AuditStatus.PLANNED
        }
    }
}
