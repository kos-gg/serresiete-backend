package com.kos.clients.blizzard

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.domain.*
import com.kos.common.HttpError
import com.kos.common.JsonParseError
import com.kos.common.NotFoundHardcoreCharacter
import com.kos.common.WithLogger
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
import java.time.OffsetDateTime

data class TokenState(val obtainedAt: OffsetDateTime, val tokenResponse: TokenResponse)

class BlizzardHttpClient(private val client: HttpClient, private val blizzardAuthClient: BlizzardAuthClient) :
    BlizzardClient, WithLogger("blizzardClient") {
    private val baseURI: (String) -> URI = { region -> URI("https://$region.api.blizzard.com") }
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private var token: Either<HttpError, TokenState>? = null

    private fun encodedName(name: String) = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())

    private suspend fun getAndUpdateToken(): Either<HttpError, TokenState> {
        val newTokenState = when (token) {
            null -> {
                logger.debug("null token state")
                blizzardAuthClient.getAccessToken()
            }

            else -> token!!.fold(
                ifLeft = {
                    logger.debug("token state with httpError: {}", it.error())
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

    private suspend fun <T> fetchFromApi(
        region: String,
        partialURI: URI,
        namespace: String,
        tokenResponse: TokenResponse,
        parseResponse: (String) -> T
    ): Either<HttpError, T> {
        val response = client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.accessToken}")
                append(HttpHeaders.Accept, "*/*")
                append("Battlenet-Namespace", "$namespace-$region")
            }
        }
        val jsonString = response.body<String>()
        return try {
            Either.Right(parseResponse(jsonString))
        } catch (e: SerializationException) {
            Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
        } catch (e: IllegalArgumentException) {
            Either.Left(json.decodeFromString<RiotError>(jsonString))
        }
    }

    override suspend fun getCharacterProfile(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowCharacterResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterProfile for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}?locale=en_US")
                val namespace = "profile-classic1x"

                val response = client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                        append(HttpHeaders.Accept, "*/*")
                        append("Battlenet-Namespace", "$namespace-$region")
                    }
                }

                if (response.status.value == 404) raise(NotFoundHardcoreCharacter(name))

                val jsonString = response.body<String>()
                try {
                    json.decodeFromString<GetWowCharacterResponse>(jsonString)
                } catch (e: SerializationException) {
                    raise(JsonParseError(jsonString, e.stackTraceToString()))
                } catch (e: IllegalArgumentException) {
                    raise(json.decodeFromString<RiotError>(jsonString))
                }
            }
        }
    }

    override suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowMediaResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterMedia for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI =
                    URI("/profile/wow/character/$realm/${encodedName(character)}/character-media?locale=en_US")
                fetchFromApi(region, partialURI, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowMediaResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowEquipmentResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterEquipment for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}/equipment?locale=en_US")
                fetchFromApi(region, partialURI, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowEquipmentResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowSpecializationsResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterEquipment for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI =
                    URI("/profile/wow/character/$realm/${encodedName(character)}/specializations?locale=en_US")
                fetchFromApi(region, partialURI, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowSpecializationsResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowCharacterStatsResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterStats for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-classic1x"
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}/statistics?locale=en_US")
                fetchFromApi(region, partialURI, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowCharacterStatsResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getItemMedia(
        region: String,
        id: Long
    ): Either<HttpError, GetWowMediaResponse> {
        return throttleRequest {
            either {
                logger.debug("getItemMedia for $id")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "static-classic1x"
                val partialURI = URI("/data/wow/media/item/$id?locale=en_US")
                fetchFromApi(region, partialURI, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowMediaResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getItem(region: String, id: Long): Either<HttpError, GetWowItemResponse> {
        return throttleRequest {
            either {
                logger.debug("getItem for $id")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/item/$id?locale=en_US")
                val namespace = "static-classic1x"
                fetchFromApi(region, partialURI, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowItemResponse>(it)
                }.bind()
            }
        }
    }

    override suspend fun getRealm(
        region: String,
        id: Long
    ): Either<HttpError, GetWowRealmResponse> {
        return throttleRequest {
            either {
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/realm/$id?locale=en_US")
                val namespace = "dynamic-classic1x"
                fetchFromApi(region, partialURI, namespace, tokenResponse.tokenResponse) {
                    json.decodeFromString<GetWowRealmResponse>(it)
                }.bind()
            }
        }
    }
}