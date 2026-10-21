package com.vm.vector.data

import com.vm.core.models.MuscleTargets
import com.vm.core.models.WorkoutExercisePlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleTargetsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializer_readsWeightedMap() {
        val plan = json.decodeFromString<WorkoutExercisePlan>(
            """{"name":"Row","target":{"back":1.0,"traps":0.5}}"""
        )
        assertEquals(mapOf("back" to 1.0, "traps" to 0.5), plan.target)
    }

    @Test
    fun serializer_readsLegacyOrderedListAsPrimaryThenSecondary() {
        val plan = json.decodeFromString<WorkoutExercisePlan>(
            """{"name":"Row","target":["back","biceps","traps"]}"""
        )
        assertEquals(listOf("back", "biceps", "traps"), plan.target.keys.toList())
        assertEquals(1.0, plan.target.getValue("back"), 0.0)
        assertEquals(0.5, plan.target.getValue("biceps"), 0.0)
        assertEquals(0.5, plan.target.getValue("traps"), 0.0)
    }

    @Test
    fun serializer_writesObject() {
        val encoded = json.encodeToString(
            WorkoutExercisePlan.serializer(),
            WorkoutExercisePlan(name = "Row", target = linkedMapOf("back" to 1.0, "biceps" to 0.5)),
        )
        val target = json.parseToJsonElement(encoded).jsonObject.getValue("target").jsonObject
        assertEquals(setOf("back", "biceps"), target.keys)
    }

    @Test
    fun legacyGroupingIdKey_readsAsSupersetGroup() {
        val plan = json.decodeFromString<WorkoutExercisePlan>("""{"name":"Curl","groupingId":"B"}""")
        assertEquals("B", plan.supersetGroup)
    }

    @Test
    fun parse_handlesLegacyCsvAndWeightedForm() {
        assertEquals(mapOf("chest" to 1.0, "triceps" to 0.5), MuscleTargets.parse("chest,triceps"))
        assertEquals(mapOf("back" to 1.0, "biceps" to 0.25), MuscleTargets.parse("back:1.0,biceps:0.25"))
        assertTrue(MuscleTargets.parse("").isEmpty())
    }

    @Test
    fun format_roundTripsThroughParse() {
        val targets = linkedMapOf("quads" to 1.0, "glutes" to 0.5)
        assertEquals(targets, MuscleTargets.parse(MuscleTargets.format(targets)))
    }
}
