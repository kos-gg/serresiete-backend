package com.kos.clients.riot

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import io.ktor.client.request.*

interface RiotClient {
    suspend fun getPUUIDByRiotId(riotName: String, riotTag: String): Either<ClientError, GetPUUIDResponse>
    suspend fun getSummonerByPuuid(puuid: String): Either<ClientError, GetSummonerResponse>
    suspend fun getMatchesByPuuid(puuid: String, queue: Int): Either<ClientError, List<String>>
    suspend fun getMatchById(matchId: String): Either<ClientError, GetMatchResponse>
    suspend fun getLeagueEntriesByPUUID(summonerId: String): Either<ClientError, List<LeagueEntryResponse>>
    suspend fun getAccountByPUUID(puuid: String): Either<ClientError, GetAccountResponse>
    suspend fun <T> fetchFromApi(
        path: String,
        parameters: HttpRequestBuilder.() -> Unit = {},
        parseResponse: (String) -> T
    ): Either<ClientError, T>
}