package com.kos.clients.riot

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.clients.fetchFromApi
import com.kos.common.WithLogger
import com.kos.datacache.domain.QueueType
import com.kos.entities.domain.LolSummoner
import com.kos.entities.domain.RiotAccount
import io.ktor.client.*
import io.ktor.client.request.*
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.kos.sources.lol.LeagueEntry
import com.kos.sources.lol.LolMatch


data class RiotHTTPClient(
    val client: HttpClient
) : RiotClient, WithLogger("riotClient") {

    private val baseURI: (String) -> URI = { region -> URI("https://$region.api.riotgames.com") }

    override suspend fun getPUUIDByRiotId(riotName: String, riotTag: String): Either<ClientError, String> {
        val encodedRiotName = URLEncoder.encode(riotName, StandardCharsets.UTF_8.toString())
        return fetchFromApi<GetPUUIDResponse> {
            client.get(url("europe", "/riot/account/v1/accounts/by-riot-id/$encodedRiotName/$riotTag"))
        }.map { it.puuid }
    }

    override suspend fun getSummonerByPuuid(puuid: String): Either<ClientError, LolSummoner> =
        fetchFromApi<GetSummonerResponse> {
            client.get(url("euw1", "/lol/summoner/v4/summoners/by-puuid/$puuid"))
        }.map { it.toLolSummoner() }

    override suspend fun getMatchesByPuuid(puuid: String, queue: QueueType): Either<ClientError, List<String>> {
        logger.debug("Getting matches for $puuid and queue $queue")
        return fetchFromApi<List<String>> {
            client.get(url("europe", "/lol/match/v5/matches/by-puuid/$puuid/ids")) {
                parameter("queue", queue.toRiotQueueId())
                parameter("count", 10)
            }
        }
    }

    override suspend fun getMatchById(matchId: String): Either<ClientError, LolMatch> {
        logger.debug("Getting match $matchId")
        return fetchFromApi<GetMatchResponse> {
            client.get(url("europe", "/lol/match/v5/matches/$matchId"))
        }.map { it.toLolMatch() }
    }

    override suspend fun getLeagueEntriesByPUUID(summonerId: String): Either<ClientError, List<LeagueEntry>> {
        logger.debug("Getting league entries for $summonerId")
        return fetchFromApi<List<LeagueEntryResponse>> {
            client.get(url("euw1", "/lol/league/v4/entries/by-puuid/$summonerId"))
        }.map { it.toLeagueEntries() }
    }

    override suspend fun getAccountByPUUID(puuid: String): Either<ClientError, RiotAccount> {
        logger.debug("Getting account for $puuid")
        return fetchFromApi<GetAccountResponse> {
            client.get(url("europe", "/riot/account/v1/accounts/by-puuid/$puuid"))
        }.map { it.toRiotAccount() }
    }

    private fun url(region: String, path: String) = baseURI(region).toString() + URI(path).toString()
}
