package com.kos.clients.domain.blizzard

import com.kos.common.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

object NameExtractorSerializer : SingleFieldSerializer<String>(
    fieldName = "name",
    extractValue = { it.requireString("name") },
    encodeValue = { builder, value -> builder.put("name", value) }
)

object EffectiveExtractorSerializer : SingleFieldSerializer<Int>(
    fieldName = "effective",
    extractValue = { it.requireInt("effective") },
    encodeValue = { builder, value -> builder.put("effective", value) }
)

object ValueExtractorSerializer : SingleFieldSerializer<Double>(
    fieldName = "value",
    extractValue = { it.requireDouble("value") },
    encodeValue = { builder, value -> builder.put("value", value) }
)

object NestedDisplayableStringExtractorSerializer : SingleFieldSerializer<String>(
    fieldName = "display.display_string",
    extractValue = { it.requireNestedString("display", "display_string") },
    encodeValue = { builder, value ->
        builder.putJsonObject("display") {
            put("display_string", value)
        }
    }
)

object DisplayableStringExtractorSerializer : SingleFieldSerializer<String>(
    fieldName = "display_string",
    extractValue = { it.requireString("display_string") },
    encodeValue = { builder, value -> builder.put("display_string", value) }
)

object DescriptionStringExtractorSerializer : SingleFieldSerializer<String>(
    fieldName = "description",
    extractValue = { it.requireString("description") },
    encodeValue = { builder, value -> builder.put("description", value) }
)

object DescriptionListSerializer : ListFieldSerializer<String>(
    elementSerializer = DescriptionStringExtractorSerializer,
    extractElement = { it.requireString("description") }
)

object DisplayStringListSerializer : ListFieldSerializer<String>(
    elementSerializer = DisplayableStringExtractorSerializer,
    extractElement = { it.requireString("display_string") }
)

object NestedDisplayableStringListSerializer : ListFieldSerializer<String>(
    elementSerializer = NestedDisplayableStringExtractorSerializer,
    extractElement = { it.requireNestedString("display", "display_string") }
)

object WowPriceSerializer : KSerializer<WowPriceResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("WowPrice") {
            element<String>("header")
            element<String>("gold")
            element<String>("silver")
            element<String>("copper")
        }

    override fun deserialize(decoder: Decoder): WowPriceResponse {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val display = jsonObject["display_strings"]!!.jsonObject
        return WowPriceResponse(
            header = display["header"]!!.jsonPrimitive.content,
            gold = display["gold"]!!.jsonPrimitive.content,
            silver = display["silver"]!!.jsonPrimitive.content,
            copper = display["copper"]!!.jsonPrimitive.content
        )
    }

    override fun serialize(encoder: Encoder, value: WowPriceResponse) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(
            buildJsonObject {
                put(
                    "display_strings",
                    buildJsonObject {
                        put("header", value.header)
                        put("gold", value.gold)
                        put("silver", value.silver)
                        put("copper", value.copper)
                    }
                )
            }
        )
    }
}
