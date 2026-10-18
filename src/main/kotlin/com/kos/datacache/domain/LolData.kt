package com.kos.datacache.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QueueType {
    SOLO_Q,
    FLEX_Q
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
