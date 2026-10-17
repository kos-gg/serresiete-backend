package com.kos.datacache

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.datacache.domain.LeagueEntry
import com.kos.datacache.domain.LeagueProfile
import com.kos.datacache.domain.LolMatch
import com.kos.datacache.domain.LolMatchParticipant
import com.kos.datacache.domain.MatchProfile
import com.kos.datacache.domain.MatchUpProfile
import com.kos.datacache.domain.QueueType
import com.kos.datacache.domain.LolData
import com.kos.entities.EntitiesTestHelper.basicLolEntity

object RiotMockHelper {
    val flexQEntry = LeagueEntry(
        QueueType.FLEX_Q,
        "GOLD",
        "I",
        1,
        13,
        14
    )
    val leagueEntries: Either<ClientError, List<LeagueEntry>> =
        Either.Right(
            listOf(
                flexQEntry,
                LeagueEntry(
                    QueueType.SOLO_Q,
                    "GOLD",
                    "I",
                    1,
                    13,
                    14
                )
            )
        )
    val matchId = "EUW1_232424252"
    val matches = Either.Right(listOf(matchId))
    val match: LolMatch =
        LolMatch(
            id = matchId,
            finishedCorrectly = true,
            duration = 1,
            participants = listOf(
                LolMatchParticipant(
                    puuid = basicLolEntity.puuid,
                    championId = 18,
                    championName = "Tristana",
                    role = "SUPPORT",
                    individualPosition = "SUPPORT",
                    teamPosition = "BOTTOM",
                    lane = "BOT",
                    kills = 1,
                    deaths = 1,
                    assists = 1,
                    assistMePings = 1,
                    visionWardsBoughtInGame = 1,
                    enemyMissingPings = 1,
                    wardsPlaced = 1,
                    totalTimeSpentDead = 25,
                    win = true
                )
            )
        )

    val riotData: LolData =
        LolData(
            summonerIcon = 1389,
            summonerLevel = 499,
            summonerName = "GTP ZeroMVPs",
            summonerTag = "KAKO",
            leagues = mapOf(
                Pair(
                    QueueType.SOLO_Q,
                    LeagueProfile(
                        teamPosition = "UTILITY",
                        tier = "GOLD",
                        rank = "I",
                        leaguePoints = 1,
                        gamesPlayed = 27,
                        winrate = 0.5925925925925926,
                        matches = listOf(
                            MatchProfile(
                                id = matchId,
                                championId = 497,
                                championName = "Rakan",
                                role = "SUPPORT",
                                individualPosition = "UTILITY",
                                teamPosition = "UTILITY",
                                lane = "BOTTOM",
                                kills = 2,
                                deaths = 7,
                                assists = 15,
                                assistMePings = 0,
                                visionWardsBoughtInGame = 8,
                                enemyMissingPings = 0,
                                wardsPlaced = 47,
                                gameFinishedCorrectly = true,
                                gameDuration = 1883,
                                totalTimeSpentDead = 174,
                                win = true,
                                matchUp = MatchUpProfile(
                                    championId = 2,
                                    championName = "Aatrox",
                                    teamPosition = "TOP",
                                    kills = 10,
                                    deaths = 1,
                                    assists = 0
                                )
                            ), MatchProfile(
                                id = matchId,
                                championId = 497,
                                championName = "Rakan",
                                role = "SUPPORT",
                                individualPosition = "UTILITY",
                                teamPosition = "UTILITY",
                                lane = "NONE",
                                kills = 0,
                                deaths = 2,
                                assists = 20,
                                assistMePings = 0,
                                visionWardsBoughtInGame = 5,
                                enemyMissingPings = 2,
                                wardsPlaced = 20,
                                gameFinishedCorrectly = true,
                                gameDuration = 1146,
                                totalTimeSpentDead = 24,
                                win = true,
                                matchUp = MatchUpProfile(
                                    championId = 2,
                                    championName = "Aatrox",
                                    teamPosition = "TOP",
                                    kills = 10,
                                    deaths = 1,
                                    assists = 0
                                )
                            ), MatchProfile(
                                id = matchId,
                                championId = 12,
                                championName = "Alistar",
                                role = "SUPPORT",
                                individualPosition = "UTILITY",
                                teamPosition = "UTILITY",
                                lane = "NONE",
                                kills = 2,
                                deaths = 2,
                                assists = 4,
                                assistMePings = 0,
                                visionWardsBoughtInGame = 6,
                                enemyMissingPings = 0,
                                wardsPlaced = 11,
                                gameFinishedCorrectly = true,
                                gameDuration = 917,
                                totalTimeSpentDead = 36,
                                win = true,
                                matchUp = MatchUpProfile(
                                    championId = 2,
                                    championName = "Aatrox",
                                    teamPosition = "TOP",
                                    kills = 10,
                                    deaths = 1,
                                    assists = 0
                                )
                            ), MatchProfile(
                                id = matchId,
                                championId = 497,
                                championName = "Rakan",
                                role = "SUPPORT",
                                individualPosition = "UTILITY",
                                teamPosition = "UTILITY",
                                lane = "BOTTOM",
                                kills = 2,
                                deaths = 3,
                                assists = 21,
                                assistMePings = 0,
                                visionWardsBoughtInGame = 16,
                                enemyMissingPings = 0,
                                wardsPlaced = 51,
                                gameFinishedCorrectly = true,
                                gameDuration = 1712,
                                totalTimeSpentDead = 67,
                                win = true,
                                matchUp = MatchUpProfile(
                                    championId = 2,
                                    championName = "Aatrox",
                                    teamPosition = "TOP",
                                    kills = 10,
                                    deaths = 1,
                                    assists = 0
                                )
                            ), MatchProfile(
                                id = matchId,
                                championId = 235,
                                championName = "Senna",
                                role = "SUPPORT",
                                individualPosition = "UTILITY",
                                teamPosition = "UTILITY",
                                lane = "BOTTOM",
                                kills = 0,
                                deaths = 2,
                                assists = 13,
                                assistMePings = 0,
                                visionWardsBoughtInGame = 11,
                                enemyMissingPings = 0,
                                wardsPlaced = 32,
                                gameFinishedCorrectly = true,
                                gameDuration = 1856,
                                totalTimeSpentDead = 73,
                                win = true,
                                matchUp = MatchUpProfile(
                                    championId = 2,
                                    championName = "Aatrox",
                                    teamPosition = "TOP",
                                    kills = 10,
                                    deaths = 1,
                                    assists = 0
                                )
                            )
                        )
                    )
                )
            )
        )

    val anotherRiotData: LolData = LolData(
        summonerIcon = 3582,
        summonerLevel = 367,
        summonerName = "sanxei",
        summonerTag = "EUW",
        leagues = mapOf()
    )


}