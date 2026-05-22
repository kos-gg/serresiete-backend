package com.kos.clients.domain.riot

import kotlinx.serialization.Serializable

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
