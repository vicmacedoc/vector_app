package com.vm.core.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private val VALID_PRIORITIES = setOf("High", "Mid", "Low", "Hold", "None")

object PrioritySerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("Priority", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val el = jsonDecoder.decodeJsonElement()
        if (el is JsonNull) return "None"
        val prim = el as? JsonPrimitive ?: return "None"
        if (!prim.isString) return "None"
        val s = prim.content
        return if (s in VALID_PRIORITIES) s else "None"
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else (encoder as JsonEncoder).encodeString(value)
    }
}
