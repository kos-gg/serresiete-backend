package com.kos.clients.riot

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import com.kos.clients.ClientError
import com.kos.clients.HttpError
import com.kos.clients.JsonParseError
import com.kos.clients.NetworkError
import com.kos.clients.domain.*
import com.kos.common.WithLogger
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

data class RiotHTTPClient(val client: HttpClient, val apiKey: String) : RiotClient, WithLogger("riotClient") {
    private val baseURI: (String) -> URI = { region -> URI("https://$region.api.riotgames.com") }
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val perSecondRateLimiter = RateLimiter.of(
        "perSecondLimiter",
        RateLimiterConfig {
            this.limitForPeriod(20)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build()
        }
    )

    private val perTwoMinuteRateLimiter = RateLimiter.of(
        "perTwoMinuteLimiter",
        RateLimiterConfig {
            this.limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(2))
                .timeoutDuration(Duration.ofMinutes(2))
                .build()
        }
    )

    private suspend fun <T> throttleRequest(request: suspend () -> T): T {
        logger.debug("PerSecondThrottlerHashId ${System.identityHashCode(perSecondRateLimiter)}")
        logger.debug("PerTwoMinutesThrottlerHashId ${System.identityHashCode(perTwoMinuteRateLimiter)}")
        return perSecondRateLimiter.executeSuspendFunction {
            perTwoMinuteRateLimiter.executeSuspendFunction(request)
        }
    }

    override suspend fun getPUUIDByRiotId(riotName: String, riotTag: String): Either<ClientError, GetPUUIDResponse> {
        return throttleRequest {
            either {
                val encodedRiotName = URLEncoder.encode(riotName, StandardCharsets.UTF_8.toString())
                val partialURI = URI("/riot/account/v1/accounts/by-riot-id/$encodedRiotName/$riotTag")

                fetchFromApi(baseURI("europe").toString() + partialURI.toString()) {
                    json.decodeFromString<GetPUUIDResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getSummonerByPuuid(puuid: String): Either<ClientError, GetSummonerResponse> {
        return throttleRequest {
            either {
                val partialURI = URI("/lol/summoner/v4/summoners/by-puuid/$puuid")

                fetchFromApi(baseURI("euw1").toString() + partialURI.toString()) {
                    json.decodeFromString<GetSummonerResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getMatchesByPuuid(puuid: String, queue: Int): Either<ClientError, List<String>> {
        return throttleRequest {

            either {
                logger.debug("Getting matches for $puuid and queue $queue")
                val partialURI = URI("/lol/match/v5/matches/by-puuid/$puuid/ids")
                fetchFromApi(
                    path = baseURI("europe").toString() + partialURI.toString(),
                    parameters = {
                        url {
                            parameters.append("queue", queue.toString())
                            parameters.append("count", "10")
                        }
                    }
                ) { jsonString ->
                    json.decodeFromString<List<String>>(jsonString)
                }.bind()
            }
        }
    }

    override suspend fun getMatchById(matchId: String): Either<ClientError, GetMatchResponse> {
        return throttleRequest {
            either {
                logger.debug("Getting match $matchId")
                val partialURI = URI("/lol/match/v5/matches/$matchId")

                fetchFromApi(baseURI("europe").toString() + partialURI.toString()) {
                    json.decodeFromString<GetMatchResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getLeagueEntriesByPUUID(summonerId: String): Either<ClientError, List<LeagueEntryResponse>> {
        return throttleRequest {
            either {
                logger.debug("Getting league entries for $summonerId")
                val partialURI = URI("/lol/league/v4/entries/by-puuid/$summonerId")

                fetchFromApi(baseURI("euw1").toString() + partialURI.toString()) {
                    json.decodeFromString<List<LeagueEntryResponse>>(it)
                }.bind()
            }
        }
    }

    override suspend fun getAccountByPUUID(puuid: String): Either<ClientError, GetAccountResponse> {
        return throttleRequest {

            either {
                logger.debug("Getting account for $puuid")
                val partialURI = URI("/riot/account/v1/accounts/by-puuid/$puuid")

                fetchFromApi(baseURI("europe").toString() + partialURI.toString()) {
                    json.decodeFromString<GetAccountResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun <T> fetchFromApi(
        path: String,
        parameters: HttpRequestBuilder.() -> Unit,
        parseResponse: (String) -> T
    ): Either<ClientError, T> {

        return Either
            .catch {
                client.get(path) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                        append("X-Riot-Token", apiKey)
                    }
                    parameters()
                }
            }
            .mapLeft {
                NetworkError(it.message ?: "Unknown network error")
            }
            .flatMap { response ->
                if (response.status.isSuccess()) {
                    val jsonString = response.body<String>()

                    Either
                        .catch { parseResponse(jsonString) }
                        .mapLeft { e ->
                            JsonParseError(
                                raw = jsonString,
                                error = e.stackTraceToString()
                            )
                        }
                } else {
                    Either.Left(
                        HttpError(
                            status = response.status.value,
                            body = response.bodyAsText()
                        )
                    )
                }
            }
    }


}