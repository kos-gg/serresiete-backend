package com.kos.clients.raiderio

import arrow.core.Either
import arrow.core.flatMap
import com.kos.clients.ClientError
import com.kos.clients.NetworkError
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CHARACTERS_PROFILE_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CLASSIC_BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_CUTOFFS_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_STATIC_DATA_PATH
import com.kos.common.WithLogger
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import java.net.URI

data class RaiderIoHTTPClient(val client: HttpClient) : RaiderIoClient, WithLogger("RaiderioClient") {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    object RaiderIoHTTPClientConstants {
        val BASE_URI = URI("https://raider.io/api/v1")
        val CLASSIC_BASE_URI = URI("https://era.raider.io/api/v1")

        const val CHARACTERS_PROFILE_PATH = "/characters/profile"
        const val MYTHIC_PLUS_STATIC_DATA_PATH = "/mythic-plus/static-data"
        const val MYTHIC_PLUS_CUTOFFS_PATH = "/mythic-plus/season-cutoffs"
    }

    override suspend fun getExpansionSeasons(expansionId: Int): Either<ClientError, ExpansionSeasons> {
        return fetchFromApi(
            path = BASE_URI.toString() + MYTHIC_PLUS_STATIC_DATA_PATH,
            parameters = {
                url {
                    parameters.append("expansion_id", expansionId.toString())
                }
            }
        ) { jsonString ->
            json.decodeFromString<ExpansionSeasons>(jsonString)
        }
    }

    override suspend fun get(wowEntity: WowEntity): Either<ClientError, RaiderIoResponse> {
        return fetchFromApi(
            path = BASE_URI.toString() + CHARACTERS_PROFILE_PATH,
            parameters = {
                url {
                    parameters.append("region", wowEntity.region)
                    parameters.append("realm", wowEntity.realm)
                    parameters.append("name", wowEntity.name)
                    parameters.append(
                        "fields",
                        "mythic_plus_scores_by_season:current,mythic_plus_best_runs:all,mythic_plus_ranks"
                    )
                }
            }) { json.decodeFromString<RaiderIoProfile>(it) }.fold(
            { clientError -> Either.Left(clientError) },
            {
                RaiderIoProtocol.getMythicPlusRanks(
                    it,
                    wowEntity.specsWithName(it.`class`),
                ).fold({ jsonError -> Either.Left(jsonError) }) { specsWithName ->
                    Either.Right(RaiderIoResponse(it, specsWithName))
                }
            })
    }

    override suspend fun exists(wowEntityRequest: WowEntityRequest): Boolean {
        val response = getRaiderioProfile(wowEntityRequest.region, wowEntityRequest.realm, wowEntityRequest.name)
        return response.status.value < 300
    }

    override suspend fun cutoff(): Either<ClientError, RaiderIoCutoff> {
        return fetchFromApi(
            path = BASE_URI.toString() + MYTHIC_PLUS_CUTOFFS_PATH,
            parameters = {
                url {
                    parameters.append("region", "eu")
                    parameters.append("season", "season-df-3")
                }
            }
        ) { cutoff ->
            RaiderIoProtocol.parseCutoffJson(cutoff)
        }
    }


    override suspend fun wowheadEmbeddedCalculator(wowEntity: WowEntity): Either<ClientError, RaiderioWowHeadEmbeddedResponse> {
        logger.debug("Getting Wowhead talents for entity {}", wowEntity)

        return fetchFromApi(
            path = CLASSIC_BASE_URI.toString() + CHARACTERS_PROFILE_PATH,
            parameters = {
                url {
                    parameters.append("region", wowEntity.region)
                    parameters.append("realm", wowEntity.realm)
                    parameters.append("name", wowEntity.name)
                    parameters.append("fields", "talents")
                }
            }) { jsonString ->
            json.decodeFromString<RaiderioWowHeadEmbeddedResponse>(jsonString)
        }

    }

    private suspend fun getRaiderioProfile(region: String, realm: String, name: String): HttpResponse =
        client.get(BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
            headers {
                append(HttpHeaders.Accept, "*/*")
            }
            url {
                parameters.append("region", region)
                parameters.append("realm", realm)
                parameters.append("name", name)
                parameters.append(
                    "fields",
                    "mythic_plus_scores_by_season:current,mythic_plus_best_runs:all,mythic_plus_ranks"
                )
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

}