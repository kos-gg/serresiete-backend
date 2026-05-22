package com.kos.clients.domain

import com.kos.clients.domain.riot.GetMatchResponse
import com.kos.clients.domain.riot.LeagueEntryResponse
import com.kos.clients.domain.riot.QueueType
import com.kos.entities.domain.LolEntity
import kotlinx.serialization.Serializable

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

data class LeagueMatchData(
    val leagueEntry: LeagueEntryResponse,
    val matchResponses: List<GetMatchResponse>,
    val matchProfiles: List<MatchProfile>
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
            lolEntity: LolEntity,
            leagues: List<LeagueMatchData>
        ): RiotData =
            RiotData(
                lolEntity.summonerIcon,
                lolEntity.summonerLevel,
                lolEntity.name,
                lolEntity.tag,
                leagues.associate { leagueMatchData ->
                    val leagueEntryResponse = leagueMatchData.leagueEntry
                    val retrievedMatches = leagueMatchData.matchResponses
                    val alreadyCachedMatches = leagueMatchData.matchProfiles
                    val gamesPlayed = leagueEntryResponse.wins + leagueEntryResponse.losses
                    val playerMatches: List<MatchProfile> =
                        retrievedMatches.flatMap { getMatchResponse ->
                            getMatchResponse.info.participants.filter { it.puuid == lolEntity.puuid }
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
