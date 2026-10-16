package com.kos.datacache.domain

import com.kos.entities.domain.LolEntity
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

data class LeagueEntry(
    val queueType: QueueType,
    val tier: String,
    val rank: String,
    val leaguePoints: Int,
    val wins: Int,
    val losses: Int
)

data class LolMatchParticipant(
    val puuid: String,
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
    val totalTimeSpentDead: Int,
    val win: Boolean
)

data class LolMatch(
    val id: String,
    val finishedCorrectly: Boolean,
    val duration: Int,
    val participants: List<LolMatchParticipant>
)

data class LeagueMatchData(
    val leagueEntry: LeagueEntry,
    val matches: List<LolMatch>,
    val matchProfiles: List<MatchProfile>
)

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
@SerialName("com.kos.clients.domain.RiotData")
data class RiotData(
    val summonerIcon: Int,
    val summonerLevel: Int,
    val summonerName: String,
    val summonerTag: String,
    val leagues: Map<QueueType, LeagueProfile>
) : Data {
    companion object {

        fun apply(
            lolEntity: LolEntity,
            leagues: List<LeagueMatchData>
        ): RiotData =
            RiotData(
                lolEntity.summonerIcon,
                lolEntity.summonerLevel,
                lolEntity.name,
                lolEntity.tag,
                leagues.associate { leagueMatchData ->
                    val leagueEntry = leagueMatchData.leagueEntry
                    val retrievedMatches = leagueMatchData.matches
                    val alreadyCachedMatches = leagueMatchData.matchProfiles
                    val gamesPlayed = leagueEntry.wins + leagueEntry.losses
                    val playerMatches: List<MatchProfile> =
                        retrievedMatches.flatMap { match ->
                            match.participants.filter { it.puuid == lolEntity.puuid }
                                .map { participant ->
                                    val matchUp =
                                        match.participants.find { it.teamPosition == participant.teamPosition && it.puuid != participant.puuid }
                                            ?.let {
                                                MatchUpProfile(
                                                    it.championId,
                                                    it.championName,
                                                    it.teamPosition,
                                                    it.kills,
                                                    it.deaths,
                                                    it.assists
                                                )
                                            }

                                    MatchProfile(
                                        match.id,
                                        participant.championId,
                                        participant.championName,
                                        participant.role,
                                        participant.individualPosition,
                                        participant.teamPosition,
                                        participant.lane,
                                        participant.kills,
                                        participant.deaths,
                                        participant.assists,
                                        participant.assistMePings,
                                        participant.visionWardsBoughtInGame,
                                        participant.enemyMissingPings,
                                        participant.wardsPlaced,
                                        match.finishedCorrectly,
                                        match.duration,
                                        participant.totalTimeSpentDead,
                                        participant.win,
                                        matchUp
                                    )
                                }
                        } + alreadyCachedMatches
                    leagueEntry.queueType to LeagueProfile(
                        playerMatches.groupBy { it.teamPosition }.mapValues { it.value.size }.maxBy { it.value }.key,
                        leagueEntry.tier,
                        leagueEntry.rank,
                        leagueEntry.leaguePoints,
                        gamesPlayed,
                        leagueEntry.wins.toDouble() / gamesPlayed,
                        playerMatches
                    )
                }
            )

    }
}
