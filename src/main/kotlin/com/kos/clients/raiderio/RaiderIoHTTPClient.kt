package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.clients.fetchFromApi
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CHARACTERS_PROFILE_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CLASSIC_BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_CUTOFFS_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_STATIC_DATA_PATH
import com.kos.common.WithLogger
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URI

data class RaiderIoHTTPClient(val client: HttpClient) : RaiderIoClient, WithLogger("RaiderioClient") {
    object RaiderIoHTTPClientConstants {
        val BASE_URI = URI("https://raider.io/api/v1")
        val CLASSIC_BASE_URI = URI("https://era.raider.io/api/v1")

        const val CHARACTERS_PROFILE_PATH = "/characters/profile"
        const val MYTHIC_PLUS_STATIC_DATA_PATH = "/mythic-plus/static-data"
        const val MYTHIC_PLUS_CUTOFFS_PATH = "/mythic-plus/season-cutoffs"
    }

    override suspend fun getExpansionSeasons(expansionId: Int): Either<ClientError, ExpansionSeasons> {

        return fetchFromApi<ExpansionSeasons> {
            client.get(BASE_URI.toString() + MYTHIC_PLUS_STATIC_DATA_PATH) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                }
                url {
                    parameters.append("expansion_id", expansionId.toString())
                }
            }
        }
    }

    override suspend fun get(wowEntity: WowEntity): Either<ClientError, RaiderIoResponse> {

        return fetchFromApi<RaiderIoProfile> {
            client.get(BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                }
                url {
                    parameters.append("region", wowEntity.region)
                    parameters.append("realm", wowEntity.realm)
                    parameters.append("name", wowEntity.name)
                    parameters.append(
                        "fields",
                        "mythic_plus_scores_by_season:current,mythic_plus_best_runs:all,mythic_plus_ranks"
                    )
                }
            }
        }.fold(
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
        val response = getRaiderIoProfile(wowEntityRequest.region, wowEntityRequest.realm, wowEntityRequest.name)
        return response.status.value < 300
    }

    override suspend fun cutoff(): Either<ClientError, RaiderIoCutoff> {
        return fetchFromApi(
            request = {
                client.get(BASE_URI.toString() + MYTHIC_PLUS_CUTOFFS_PATH) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                    }
                    url {
                        parameters.append("region", "eu")
                        parameters.append("season", "season-df-3")
                    }
                }
            },
            parseResponse = { cutoff ->
                RaiderIoProtocol.parseCutoffJson(cutoff)
            }
        )
    }


    override suspend fun wowheadEmbeddedCalculator(wowEntity: WowEntity): Either<ClientError, RaiderioWowHeadEmbeddedResponse> {
        logger.debug("Getting Wowhead talents for entity {}", wowEntity)

        return fetchFromApi<RaiderioWowHeadEmbeddedResponse> {
            client.get(CLASSIC_BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                }
                url {
                    parameters.append("region", wowEntity.region)
                    parameters.append("realm", wowEntity.realm)
                    parameters.append("name", wowEntity.name)
                    parameters.append("fields", "talents")
                }
            }
        }
    }

    private suspend fun getRaiderIoProfile(region: String, realm: String, name: String): HttpResponse =
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

}