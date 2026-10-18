package com.kos.datacache.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = QueueTypeSerializer::class)
enum class QueueType {
    SOLO_Q {
        override fun toString(): String = "RANKED_SOLO_5x5"
    },
    FLEX_Q {
        override fun toString(): String = "RANKED_FLEX_SR"
    }
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
data class MatchUpProfile(
    val championId: Int,
    val championName: String,
    val teamPosition: String,
    val kills: Int,
    val deaths: Int,
    val assists: Int
)

@Serializable
data class MatchProfile(
    val id: String,
    val championId: Int,
    val championName: String,
    val role: String,
    val individualPosition: String,
    val teamPosition: String,
    val lane: String,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val assistMePings: Int,
    val visionWardsBoughtInGame: Int,
    val enemyMissingPings: Int,
    val wardsPlaced: Int,
    val gameFinishedCorrectly: Boolean,
    val gameDuration: Int,
    val totalTimeSpentDead: Int,
    val win: Boolean,
    val matchUp: MatchUpProfile?
)

@Serializable
data class LeagueProfile(
    val teamPosition: String,
    val tier: String,
    val rank: String,
    val leaguePoints: Int,
    val gamesPlayed: Int,
    val winrate: Double,
    val matches: List<MatchProfile>
)

@Serializable
@SerialName("lol")
data class LolData(
    val summonerIcon: Int,
    val summonerLevel: Int,
    val summonerName: String,
    val summonerTag: String,
    val leagues: Map<QueueType, LeagueProfile>
) : Data
