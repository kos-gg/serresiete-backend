package com.kos.clients.riot

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.datacache.domain.LeagueEntry
import com.kos.datacache.domain.LolMatch
import com.kos.datacache.domain.QueueType
import com.kos.entities.domain.LolSummoner
import com.kos.entities.domain.RiotAccount

interface RiotClient {
    suspend fun getPUUIDByRiotId(riotName: String, riotTag: String): Either<ClientError, String>
    suspend fun getSummonerByPuuid(puuid: String): Either<ClientError, LolSummoner>
    suspend fun getMatchesByPuuid(puuid: String, queue: QueueType): Either<ClientError, List<String>>
    suspend fun getMatchById(matchId: String): Either<ClientError, LolMatch>
    suspend fun getLeagueEntriesByPUUID(summonerId: String): Either<ClientError, List<LeagueEntry>>
    suspend fun getAccountByPUUID(puuid: String): Either<ClientError, RiotAccount>
}