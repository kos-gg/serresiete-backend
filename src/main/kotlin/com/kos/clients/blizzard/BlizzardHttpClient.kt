package com.kos.clients.blizzard

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import com.kos.clients.ClientError
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
import java.time.OffsetDateTime

data class TokenState(val obtainedAt: OffsetDateTime, val tokenResponse: TokenResponse)

class BlizzardHttpClient(private val client: HttpClient, private val blizzardAuthClient: BlizzardAuthClient) :
    BlizzardClient, WithLogger("blizzardClient") {
    private val baseURI: (String) -> URI = { region -> URI("https://$region.api.blizzard.com") }
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private var token: Either<ClientError, TokenState>? = null

    override suspend fun getCharacterProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterMedia for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI =
                    URI("/profile/wow/character/$realm/${encodedName(character)}/character-media?locale=en_US")
                val path = (baseURI(region).toString() + partialURI.toString()).lowercase()

                fetchFromApi(path, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowCharacterResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowMediaResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterMedia for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI =
                    URI("/profile/wow/character/$realm/${encodedName(character)}/character-media?locale=en_US")
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowMediaResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowEquipmentResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterEquipment for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}/equipment?locale=en_US")
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowEquipmentResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowSpecializationsResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterSpecialitzation for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI =
                    URI("/profile/wow/character/$realm/${encodedName(character)}/specializations?namespace=profile-classic1x-eu&locale=en_US")
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowSpecializationsResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterStatsResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterStats for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}/statistics?locale=en_US")
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowCharacterStatsResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getItemMedia(
        region: String,
        id: Long
    ): Either<ClientError, GetWowMediaResponse> {
        return throttleRequest {
            either {
                logger.debug("getItemMedia for $id")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "static-classic"
                val partialURI = URI("/data/wow/media/item/$id?locale=en_US")
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowMediaResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getItem(region: String, id: Long): Either<ClientError, GetWowItemResponse> {
        return throttleRequest {
            either {
                logger.debug("getItem for $id")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/item/$id?locale=en_US")
                val namespace = "static-classic"
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowItemResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getRealm(
        region: String,
        id: Long
    ): Either<ClientError, GetWowRealmResponse> {
        return throttleRequest {
            either {
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/realm/$id?locale=en_US")
                val namespace = "dynamic-classic1x"
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowRealmResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GetWowRosterResponse> {
        return throttleRequest {
            either {
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/guild/$realm/$guild/roster?namespace=profile-classic1x-eu&locale=en_US")
                val namespace = "profile-classic1x-eu"
                fetchFromApi(
                    (baseURI(region).toString() + partialURI.toString()).lowercase(),
                    namespace,
                    tokenResponse.tokenResponse
                ) {
                    json.decodeFromString<GetWowRosterResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun <T> fetchFromApi(
        path: String,
        namespace: String,
        tokenResponse: TokenResponse,
        parseResponse: (String) -> T
    ): Either<ClientError, T> {

        return Either.catch {
            client.get(path) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${tokenResponse.accessToken}")
                    append(HttpHeaders.Accept, "*/*")
                    append("Battlenet-Namespace", namespace)
                }
            }
        }.mapLeft {
            NetworkError(it.message ?: "Unknown network error")
        }.flatMap { response ->
            if (response.status.isSuccess()) {
                val jsonString = response.body<String>()

                Either
                    .catch { parseResponse(jsonString) }
                    .mapLeft { e ->
                        com.kos.clients.JsonParseError(
                            raw = jsonString,
                            error = e.stackTraceToString()
                        )
                    }

            } else {
                Either.Left(
                    com.kos.clients.HttpError(
                        status = response.status.value,
                        body = response.bodyAsText()
                    )
                )
            }
        }
    }

    private suspend fun getAndUpdateToken(): Either<ClientError, TokenState> {
        val newTokenState = when (token) {
            null -> {
                logger.debug("null token state")
                blizzardAuthClient.getAccessToken()
            }

            else -> token!!.fold(
                ifLeft = {
                    logger.debug("token state with ClientError: {}", it.toString())
                    blizzardAuthClient.getAccessToken()
                },
                ifRight = {
                    if (it.obtainedAt.plusSeconds(it.tokenResponse.expiresIn)
                            .minusSeconds(10)
                            .isBefore(OffsetDateTime.now())
                    ) {
                        logger.debug(
                            "token state expired: expiresIn - {} obtainedAt - {} ",
                            it.tokenResponse.expiresIn,
                            it.obtainedAt
                        )
                        blizzardAuthClient.getAccessToken()
                    } else {
                        logger.debug("token in good state")
                        Either.Right(it.tokenResponse)
                    }
                }
            )
        }.map {
            TokenState(OffsetDateTime.now(), it)
        }
        token = newTokenState
        return newTokenState
    }

    private val perSecondRateLimiter = RateLimiter.of(
        "perSecondLimiter",
        RateLimiterConfig {
            this.limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build()
        }
    )

    private suspend fun <T> throttleRequest(request: suspend () -> T): T {
        return perSecondRateLimiter.executeSuspendFunction(request)
    }

    private fun encodedName(name: String) = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())

}