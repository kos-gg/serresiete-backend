package com.kos.clients.riot

import arrow.core.Either
import com.kos.common.HttpError
import com.kos.common.JsonParseError
import com.kos.common.WithLogger
import com.kos.clients.domain.*
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerializationException
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

    override suspend fun getPUUIDByRiotId(riotName: String, riotTag: String): Either<HttpError, GetPUUIDResponse> {
        return throttleRequest {
            val encodedRiotName = URLEncoder.encode(riotName, StandardCharsets.UTF_8.toString())

            val partialURI = URI("/riot/account/v1/accounts/by-riot-id/$encodedRiotName/$riotTag")
            val response = client.get(baseURI("europe").toString() + partialURI.toString()) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                    append("X-Riot-Token", apiKey)
                }
            }
            val jsonString = response.body<String>()
            try {
                Either.Right(json.decodeFromString<GetPUUIDResponse>(jsonString))
            } catch (e: SerializationException) {
                Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
            } catch (e: IllegalArgumentException) {
                val error = json.decodeFromString<RiotError>(jsonString)
                Either.Left(error)
            }
        }
    }

    override suspend fun getSummonerByPuuid(puuid: String): Either<HttpError, GetSummonerResponse> {
        return throttleRequest {
            val partialURI = URI("/lol/summoner/v4/summoners/by-puuid/$puuid")

            val response = client.get(baseURI("euw1").toString() + partialURI.toString()) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                    append("X-Riot-Token", apiKey)
                }
            }
            val jsonString = response.body<String>()
            try {
                Either.Right(json.decodeFromString<GetSummonerResponse>(jsonString))
            } catch (e: SerializationException) {
                Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
            } catch (e: IllegalArgumentException) {
                val error = json.decodeFromString<RiotError>(jsonString)
                Either.Left(error)
            }
        }
    }

    override suspend fun getMatchesByPuuid(puuid: String, queue: Int): Either<HttpError, List<String>> {
        return throttleRequest {
            logger.debug("Getting matches for $puuid and queue $queue")
            val partialURI = URI("/lol/match/v5/matches/by-puuid/$puuid/ids")
            val response = client.get(baseURI("europe").toString() + partialURI.toString()) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                    append("X-Riot-Token", apiKey)
                }
                url {
                    parameters.append("queue", queue.toString())
                    parameters.append(
                        "count",
                        "10"
                    )
                }

            }
            val jsonString = response.body<String>()
            try {
                Either.Right(json.decodeFromString<List<String>>(jsonString))
            } catch (e: SerializationException) {
                Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
            } catch (e: IllegalArgumentException) {
                val error = json.decodeFromString<RiotError>(jsonString)
                Either.Left(error)
            }
        }
    }

    override suspend fun getMatchById(matchId: String): Either<HttpError, GetMatchResponse> {
        return throttleRequest {
            logger.debug("Getting match $matchId")
            val partialURI = URI("/lol/match/v5/matches/$matchId")
            val response = client.get(baseURI("europe").toString() + partialURI.toString()) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                    append("X-Riot-Token", apiKey)
                }
            }
            val jsonString = response.body<String>()
            try {
                Either.Right(json.decodeFromString<GetMatchResponse>(jsonString))
            } catch (e: SerializationException) {
                Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
            } catch (e: IllegalArgumentException) {
                val error = json.decodeFromString<RiotError>(jsonString)
                Either.Left(error)
            }
        }
    }

    override suspend fun getLeagueEntriesByPUUID(puuid: String): Either<HttpError, List<LeagueEntryResponse>> {
        return throttleRequest {
            logger.debug("Getting league entries for $puuid")
            val partialURI = URI("/lol/league/v4/entries/by-puuid/$puuid")
            val response = client.get(baseURI("euw1").toString() + partialURI.toString()) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                    append("X-Riot-Token", apiKey)
                }
            }
            val jsonString = response.body<String>()
            try {
                Either.Right(json.decodeFromString<List<LeagueEntryResponse>>(jsonString))
            } catch (e: SerializationException) {
                Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
            } catch (e: IllegalArgumentException) {
                val error = json.decodeFromString<RiotError>(jsonString)
                Either.Left(error)
            }
        }
    }

    override suspend fun getAccountByPUUID(puuid: String): Either<HttpError, GetAccountResponse> {
        return throttleRequest {
            logger.debug("Getting account for $puuid")
            val partialURI = URI("/riot/account/v1/accounts/by-puuid/$puuid")
            val response = client.get(baseURI("europe").toString() + partialURI.toString()) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                    append("X-Riot-Token", apiKey)
                }
            }
            val jsonString = response.body<String>()
            try {
                Either.Right(json.decodeFromString<GetAccountResponse>(jsonString))
            } catch (e: SerializationException) {
                Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
            } catch (e: IllegalArgumentException) {
                val error = json.decodeFromString<RiotError>(jsonString)
                Either.Left(error)
            }
        }
    }

}