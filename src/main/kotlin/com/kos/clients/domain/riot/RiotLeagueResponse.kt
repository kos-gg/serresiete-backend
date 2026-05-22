package com.kos.clients.domain.riot

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = QueueTypeSerializer::class)
enum class QueueType {
    SOLO_Q {
        override fun toInt(): Int = 420
        override fun toString(): String = "RANKED_SOLO_5x5"
    },
    FLEX_Q {
        override fun toInt(): Int = 440
        override fun toString(): String = "RANKED_FLEX_SR"
    };

    abstract fun toInt(): Int
}

object QueueTypeSerializer : KSerializer<QueueType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("QueueType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: QueueType) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): QueueType {
        return when (val queueType = decoder.decodeString()) {
            "RANKED_SOLO_5x5" -> QueueType.SOLO_Q
            "RANKED_FLEX_SR" -> QueueType.FLEX_Q
            else -> throw IllegalArgumentException("Unknown queue type: $queueType")
        }
    }
}

@Serializable
data class LeagueEntryResponse(
    val queueType: QueueType,
    val tier: String,
    val rank: String,
    val leaguePoints: Int,
    val wins: Int,
    val losses: Int,
    val hotStreak: Boolean
)
