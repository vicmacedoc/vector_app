package com.vm.core.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/**
 * Weighted muscle targets. Storage form on [WorkoutSet.targetMuscles] is `back:1.0,biceps:0.5`.
 * Legacy CSV without weights (`chest,triceps`) reads as first = primary (1.0), rest = secondary (0.5).
 */
object MuscleTargets {
    const val PRIMARY = 1.0
    const val SECONDARY = 0.5

    fun fromOrderedTags(tags: List<String>): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        tags.map { normalize(it) }.filter { it.isNotEmpty() }.forEachIndexed { index, tag ->
            if (tag !in out) out[tag] = if (index == 0) PRIMARY else SECONDARY
        }
        return out
    }

    fun parse(csv: String): Map<String, Double> {
        val parts = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyMap()
        if (parts.none { ':' in it }) return fromOrderedTags(parts)
        val out = LinkedHashMap<String, Double>()
        parts.forEach { part ->
            val name = normalize(part.substringBefore(':'))
            val weight = part.substringAfter(':', "").trim().toDoubleOrNull() ?: PRIMARY
            if (name.isNotEmpty() && weight > 0.0) out[name] = (out[name] ?: 0.0) + weight
        }
        return out
    }

    fun format(targets: Map<String, Double>): String =
        targets.entries
            .filter { it.key.isNotBlank() && it.value > 0.0 }
            .joinToString(",") { (name, weight) -> "${normalize(name)}:${formatWeight(weight)}" }

    fun normalize(raw: String): String = raw.trim().lowercase(Locale.US).replace(' ', '_')

    private fun formatWeight(weight: Double): String =
        if (weight == weight.toLong().toDouble()) "${weight.toLong()}.0" else weight.toString()
}

/** Reads `target` as either a `{muscle: weight}` object or a legacy ordered list; always writes an object. */
object MuscleTargetSerializer : KSerializer<Map<String, Double>> {
    private val delegate = MapSerializer(String.serializer(), Double.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Map<String, Double> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> emptyMap()
            is JsonArray -> MuscleTargets.fromOrderedTags(
                element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            )
            is JsonObject -> {
                val out = LinkedHashMap<String, Double>()
                element.forEach { (name, value) ->
                    val weight = (value as? JsonPrimitive)?.doubleOrNull ?: MuscleTargets.PRIMARY
                    val key = MuscleTargets.normalize(name)
                    if (key.isNotEmpty() && weight > 0.0) out[key] = weight
                }
                out
            }
            is JsonPrimitive -> element.contentOrNull
                ?.split(',')
                ?.let { MuscleTargets.fromOrderedTags(it) }
                .orEmpty()
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, Double>) {
        val jsonEncoder = encoder as? JsonEncoder ?: return delegate.serialize(encoder, value)
        jsonEncoder.encodeJsonElement(JsonObject(value.mapValues { JsonPrimitive(it.value) }))
    }
}
