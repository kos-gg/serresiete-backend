package com.kos.sources.lol

import com.kos.datacache.domain.LeagueProfile
import com.kos.datacache.domain.LolData
import com.kos.datacache.domain.MatchProfile
import com.kos.datacache.domain.MatchUpProfile
import com.kos.datacache.domain.QueueType
import com.kos.entities.domain.LolEntity

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

fun List<LeagueMatchData>.toLolData(lolEntity: LolEntity): LolData =
    LolData(
        lolEntity.summonerIcon,
        lolEntity.summonerLevel,
        lolEntity.name,
        lolEntity.tag,
        associate { leagueMatchData ->
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
