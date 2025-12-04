package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CHARACTERS_PROFILE_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CLASSIC_BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_CUTOFFS_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_STATIC_DATA_PATH
import com.kos.common.HttpError
import com.kos.common.JsonParseError
import com.kos.common.RaiderIoError
import com.kos.common.WithLogger
import com.kos.entities.WowEntity
import com.kos.entities.WowEntityRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerializationException
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

    override suspend fun getExpansionSeasons(expansionId: Int): Either<HttpError, ExpansionSeasons> {
        val jsonResponse = client.get(BASE_URI.toString() + MYTHIC_PLUS_STATIC_DATA_PATH) {
            headers {
                append(HttpHeaders.Accept, "*/*")
            }
            url {
                parameters.append("expansion_id", expansionId.toString())
            }
        }.body<String>()

        return try {
            Either.Right(json.decodeFromString<ExpansionSeasons>(jsonResponse))
        } catch (e: SerializationException) {
            Either.Left(JsonParseError(jsonResponse, e.stackTraceToString()))
        } catch (e: IllegalArgumentException) {
            Either.Left(json.decodeFromString<RaiderIoError>(jsonResponse))
        }
    }

    override suspend fun get(wowEntity: WowEntity): Either<HttpError, RaiderIoResponse> {
        val response = getRaiderioProfile(wowEntity.region, wowEntity.realm, wowEntity.name)
        val jsonString = response.body<String>()
        val decodedResponse: Either<HttpError, RaiderIoProfile> = responseToEitherErrorOrProfile(jsonString)

        return decodedResponse.fold({ httpError -> Either.Left(httpError) }) {
            RaiderIoProtocol.parseMythicPlusRanks(
                jsonString,
                wowEntity.specsWithName(it.`class`),
                it.seasonScores[0].scores
            ).fold({ jsonError -> Either.Left(jsonError) }) { specsWithName ->
                Either.Right(RaiderIoResponse(it, specsWithName))
            }
        }
    }

    override suspend fun exists(wowEntityRequest: WowEntityRequest): Boolean {
        val response = getRaiderioProfile(wowEntityRequest.region, wowEntityRequest.realm, wowEntityRequest.name)
        return response.status.value < 300
    }

    override suspend fun cutoff(): Either<HttpError, RaiderIoCutoff> {
        val response = client.get(BASE_URI.toString() + MYTHIC_PLUS_CUTOFFS_PATH) {
            headers {
                append(HttpHeaders.Accept, "*/*")
            }
            url {
                parameters.append("region", "eu")
                parameters.append("season", "season-df-3")
            }
        }
        val jsonString = response.body<String>()
        return RaiderIoProtocol.parseCutoffJson(jsonString)
    }

    override suspend fun wowheadEmbeddedCalculator(wowEntity: WowEntity): Either<HttpError, RaiderioWowHeadEmbeddedResponse> {
        logger.debug("Getting Wowhead talents for entity {}", wowEntity)

        val url = CLASSIC_BASE_URI.toString() + CHARACTERS_PROFILE_PATH
        val response = client.get(url) {
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
        val jsonString = response.body<String>()
        return try {
            Either.Right(json.decodeFromString<RaiderioWowHeadEmbeddedResponse>(jsonString))
        } catch (e: SerializationException) {
            Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
        } catch (e: IllegalArgumentException) {
            Either.Left(json.decodeFromString<RaiderIoError>(jsonString))
        }
    }

    private fun responseToEitherErrorOrProfile(jsonString: String) = try {
        Either.Right(json.decodeFromString<RaiderIoProfile>(jsonString))
    } catch (e: SerializationException) {
        Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
    } catch (e: IllegalArgumentException) {
        Either.Left(json.decodeFromString<RaiderIoError>(jsonString))
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
}