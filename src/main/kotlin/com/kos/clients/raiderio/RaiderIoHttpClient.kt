package com.kos.clients.raiderio

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.clients.fetchFromApi
import com.kos.clients.raiderio.RaiderIoHttpClient.RaiderIoHttpClientConstants.BASE_URI
import com.kos.clients.raiderio.RaiderIoHttpClient.RaiderIoHttpClientConstants.CHARACTERS_PROFILE_PATH
import com.kos.clients.raiderio.RaiderIoHttpClient.RaiderIoHttpClientConstants.CLASSIC_BASE_URI
import com.kos.clients.raiderio.RaiderIoHttpClient.RaiderIoHttpClientConstants.MYTHIC_PLUS_CUTOFFS_PATH
import com.kos.clients.raiderio.RaiderIoHttpClient.RaiderIoHttpClientConstants.MYTHIC_PLUS_STATIC_DATA_PATH
import com.kos.common.WithLogger
import com.kos.entities.domain.WowEntity
import com.kos.sources.wow.staticdata.wowseason.Season
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.net.URI
import com.kos.sources.wow.SeasonCutoff
import com.kos.sources.wow.WowCharacterProfile

class RaiderIoHttpClient(
    val client: HttpClient
) : RaiderIoClient, WithLogger("raiderIoClient") {
    object RaiderIoHttpClientConstants {
        val BASE_URI = URI("https://raider.io/api/v1")
        val CLASSIC_BASE_URI = URI("https://era.raider.io/api/v1")

        const val CHARACTERS_PROFILE_PATH = "/characters/profile"
        const val MYTHIC_PLUS_STATIC_DATA_PATH = "/mythic-plus/static-data"
        const val MYTHIC_PLUS_CUTOFFS_PATH = "/mythic-plus/season-cutoffs"
    }

    override suspend fun getExpansionSeasons(expansionId: Int): Either<ClientError, List<Season>> =
        fetchFromApi<ExpansionSeasons> {
            client.get(BASE_URI.toString() + MYTHIC_PLUS_STATIC_DATA_PATH) {
                parameter("expansion_id", expansionId)
            }
        }.map { expansionSeasons -> expansionSeasons.seasons.map { it.toSeason() } }

    override suspend fun get(wowEntity: WowEntity): Either<ClientError, WowCharacterProfile> = either {
        val profile = fetchFromApi<RaiderIoProfile> {
            client.get(BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
                parameter("region", wowEntity.region)
                parameter("realm", wowEntity.realm)
                parameter("name", wowEntity.name)
                parameter(
                    "fields",
                    "mythic_plus_scores_by_season:current,mythic_plus_best_runs:all,mythic_plus_ranks,mythic_plus_recent_runs"
                )
            }
        }.bind()
        val specsWithName = RaiderIoProtocol.getMythicPlusRanks(profile, wowEntity.specsWithName(profile.`class`)).bind()
        profile.toWowCharacterProfile(specsWithName)
    }

    override suspend fun cutoff(seasonSlug: String): Either<ClientError, SeasonCutoff> {
        return fetchFromApi(
            request = {
                client.get(BASE_URI.toString() + MYTHIC_PLUS_CUTOFFS_PATH) {
                    retry { noRetry() }
                    parameter("region", "eu")
                    parameter("season", seasonSlug)
                }
            },
            parseResponse = { cutoff ->
                RaiderIoProtocol.parseCutoffJson(cutoff)
            }
        )
    }

    override suspend fun wowheadEmbeddedCalculator(
        region: String,
        realm: String,
        name: String
    ): Either<ClientError, String> {
        logger.debug("Getting Wowhead talents for {}/{}/{}", region, realm, name)
        return fetchFromApi<RaiderioWowHeadEmbeddedResponse> {
            client.get(CLASSIC_BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
                parameter("region", region)
                parameter("realm", realm)
                parameter("name", name)
                parameter("fields", "talents")
            }
        }.map { it.talentLoadout.wowheadCalculator }
    }
}
