package com.kos.clients.domain

import com.kos.characters.LolCharacter
import com.kos.common.HttpError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class GetAccountResponse(val gameName: String, val tagLine: String)

@Serializable
data class GetPUUIDResponse(val puuid: String, val gameName: String, val tagLine: String)

@Serializable
data class GetSummonerResponse(
    val id: String,
    val accountId: String,
    val puuid: String,
    val profileIconId: Int,
    val revisionDate: Long,
    val summonerLevel: Int
)

@Serializable
data class MatchParticipant(
    val assistMePings: Int,
    val puuid: String,
    val visionWardsBoughtInGame: Int,
    val wardsPlaced: Int,
    val visionScore: Int,
    val role: String,
    val individualPosition: String,
    val teamPosition: String,
    val lane: String,
    val kills: Int,
    val enemyMissingPings: Int,
    val deaths: Int,
    val championId: Int,
    val championName: String,
    val assists: Int,
    val totalTimeSpentDead: Int,
    val totalMinionsKilled: Int,
    val goldEarned: Int,
    val win: Boolean
)

@Serializable
data class Metadata(
    val matchId: String
)

@Serializable
data class MatchInfo(
    val endOfGameResult: String,
    val gameDuration: Int,
    val mapId: Int,
    val participants: List<MatchParticipant>
)

@Serializable
data class GetMatchResponse(
    val info: MatchInfo,
    val metadata: Metadata
)

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

@Serializable
data class RiotStatus(
    @SerialName("status_code")
    val statusCode: Int,
    val message: String
)

@Serializable
data class RiotError(val status: RiotStatus) : HttpError {
    override fun error(): String = "${status.statusCode} ${status.message}"
}

data class LeagueMatchData(
    val leagueEntry: LeagueEntryResponse,
    val matchResponses: List<GetMatchResponse>,
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
data class RiotData(
    val summonerIcon: Int,
    val summonerLevel: Int,
    val summonerName: String,
    val summonerTag: String,
    val leagues: Map<QueueType, LeagueProfile>
) : Data {
    companion object {

        fun apply(
            lolCharacter: LolCharacter,
            leagues: List<LeagueMatchData>
        ): RiotData =
            RiotData(
                lolCharacter.summonerIcon,
                lolCharacter.summonerLevel,
                lolCharacter.name,
                lolCharacter.tag,
                leagues.associate { leagueMatchData ->
                    val leagueEntryResponse = leagueMatchData.leagueEntry
                    val retrievedMatches = leagueMatchData.matchResponses
                    val alreadyCachedMatches = leagueMatchData.matchProfiles
                    val gamesPlayed = leagueEntryResponse.wins + leagueEntryResponse.losses
                    val playerMatches: List<MatchProfile> =
                        retrievedMatches.flatMap { getMatchResponse ->
                            getMatchResponse.info.participants.filter { it.puuid == lolCharacter.puuid }
                                .map { participant ->
                                    val matchUp =
                                        getMatchResponse.info.participants.find { it.teamPosition == participant.teamPosition && it.puuid != participant.puuid }
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
                                        getMatchResponse.metadata.matchId,
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
                                        getMatchResponse.info.endOfGameResult == "GameComplete",
                                        getMatchResponse.info.gameDuration,
                                        participant.totalTimeSpentDead,
                                        participant.win,
                                        matchUp
                                    )
                                }
                        } + alreadyCachedMatches
                    leagueEntryResponse.queueType to LeagueProfile(
                        playerMatches.groupBy { it.teamPosition }.mapValues { it.value.size }.maxBy { it.value }.key,
                        leagueEntryResponse.tier,
                        leagueEntryResponse.rank,
                        leagueEntryResponse.leaguePoints,
                        gamesPlayed,
                        leagueEntryResponse.wins.toDouble() / gamesPlayed,
                        playerMatches
                    )
                }
            )

    }
}