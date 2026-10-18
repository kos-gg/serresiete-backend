package com.kos.clients.riot

import com.kos.clients.domain.GetAccountResponse
import com.kos.clients.domain.GetMatchResponse
import com.kos.clients.domain.GetSummonerResponse
import com.kos.clients.domain.LeagueEntryResponse
import com.kos.datacache.domain.QueueType
import com.kos.entities.domain.LolSummoner
import com.kos.entities.domain.RiotAccount
import com.kos.sources.lol.LeagueEntry
import com.kos.sources.lol.LolMatchParticipant
import com.kos.sources.lol.LolMatch

fun GetSummonerResponse.toLolSummoner() = LolSummoner(puuid, profileIconId, summonerLevel)

fun GetAccountResponse.toRiotAccount() = RiotAccount(gameName, tagLine)

fun QueueType.toRiotQueueId() = when (this) {
    QueueType.SOLO_Q -> 420
    QueueType.FLEX_Q -> 440
}

private fun riotQueueType(queueType: String) = when (queueType) {
    "RANKED_SOLO_5x5" -> QueueType.SOLO_Q
    "RANKED_FLEX_SR" -> QueueType.FLEX_Q
    else -> null
}

fun List<LeagueEntryResponse>.toLeagueEntries() = mapNotNull { entry ->
    riotQueueType(entry.queueType)?.let {
        LeagueEntry(it, entry.tier, entry.rank, entry.leaguePoints, entry.wins, entry.losses)
    }
}

fun GetMatchResponse.toLolMatch() = LolMatch(
    id = metadata.matchId,
    finishedCorrectly = info.endOfGameResult == "GameComplete",
    duration = info.gameDuration,
    participants = info.participants.map {
        LolMatchParticipant(
            puuid = it.puuid,
            championId = it.championId,
            championName = it.championName,
            role = it.role,
            individualPosition = it.individualPosition,
            teamPosition = it.teamPosition,
            lane = it.lane,
            kills = it.kills,
            deaths = it.deaths,
            assists = it.assists,
            assistMePings = it.assistMePings,
            visionWardsBoughtInGame = it.visionWardsBoughtInGame,
            enemyMissingPings = it.enemyMissingPings,
            wardsPlaced = it.wardsPlaced,
            totalTimeSpentDead = it.totalTimeSpentDead,
            win = it.win
        )
    }
)
