package com.kos.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

open class SingleFieldSerializer<T>(
    private val fieldName: String,
    private val extractValue: (JsonObject) -> T,
    private val encodeValue: (JsonEncoder, T) -> Unit
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SingleFieldSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        return extractValue(jsonObject)
    }

    override fun serialize(encoder: Encoder, value: T) {
        require(encoder is JsonEncoder)
        encodeValue(encoder, value)
    }
}

open class ListFieldSerializer<T>(
    private val elementSerializer: KSerializer<T>,
    private val extractElement: (JsonObject) -> T
) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor =
        ListSerializer(elementSerializer).descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        require(decoder is JsonDecoder)
        val jsonArray = decoder.decodeJsonElement().jsonArray
        return jsonArray.map { element -> extractElement(element.jsonObject) }
    }

    override fun serialize(encoder: Encoder, value: List<T>) {
        require(encoder is JsonEncoder)
        encoder.encodeSerializableValue(ListSerializer(elementSerializer), value)
    }
}