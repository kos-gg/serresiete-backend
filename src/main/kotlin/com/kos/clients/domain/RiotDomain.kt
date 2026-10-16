package com.kos.clients.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetAccountResponse(val gameName: String, val tagLine: String)

@Serializable
data class GetPUUIDResponse(val puuid: String, val gameName: String, val tagLine: String)

@Serializable
data class GetSummonerResponse(
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

@Serializable
data class LeagueEntryResponse(
    val queueType: String,
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
