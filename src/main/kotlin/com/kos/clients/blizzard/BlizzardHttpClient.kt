package com.kos.clients.blizzard

import arrow.core.Either
import arrow.core.flatMap
import com.kos.clients.ClientError
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.BATTLENET_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.DYNAMIC_CLASSIC1X_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.PROFILE_CLASSIC1X_EU_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.PROFILE_CLASSIC1X_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.STATIC_CLASSIC_NAMESPACE
import com.kos.clients.domain.*
import com.kos.clients.fetchFromApi
import com.kos.common.WithLogger
import com.kos.datacache.domain.WowSpecialization
import com.kos.datacache.domain.WowStats
import com.kos.entities.domain.GuildRoster
import com.kos.entities.domain.WowCharacter
import com.kos.entities.domain.WowForeverCharacter
import com.kos.entities.domain.WowRealm
import com.kos.sources.wow.MythicKeystoneProfile
import com.kos.sources.wowhc.WowEquippedItemRef
import com.kos.sources.wowhc.WowItemDetails
import io.ktor.client.*
import io.ktor.client.request.*
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BlizzardHttpClient(
    private val client: HttpClient
) : BlizzardClient, WithLogger("blizzardClient") {

    object BlizzardHttpClientConstants {
        const val PROFILE_CLASSIC1X_NAMESPACE = "profile-classic1x"
        const val BATTLENET_NAMESPACE = "Battlenet-Namespace"
        const val STATIC_CLASSIC_NAMESPACE = "static-classic"
        const val DYNAMIC_CLASSIC1X_NAMESPACE = "dynamic-classic1x"
        const val PROFILE_CLASSIC1X_EU_NAMESPACE = "profile-classic1x-eu"
    }

    private val baseURI: (String) -> URI = { region -> URI("https://$region.api.blizzard.com") }

    override suspend fun getClassicProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, WowCharacter> {
        logger.debug("getClassicProfile for $region $realm $character")
        return fetchFromApi<GetWowCharacterResponse> {
            client.get(url(region, "/profile/wow/character/$realm/${encodedName(character)}?locale=en_US")) {
                header(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
            }
        }.map { it.toWowCharacter() }
    }

    override suspend fun getRetailProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, WowCharacter> {
        logger.debug("getRetailProfile for $region $realm $character")
        return fetchFromApi<GetWowCharacterResponse> {
            client.get(url(region, "/profile/wow/character/$realm/${encodedName(character)}?locale=en_US")) {
                header(BATTLENET_NAMESPACE, "profile-$region")
            }
        }.map { it.toWowCharacter() }
    }

    override suspend fun getForeverProfile(
        region: String,
        name: String,
        surname: String
    ): Either<ClientError, WowForeverCharacter> {
        logger.debug("getForeverProfile for $region $name $surname")
        return fetchFromApi<GetWowForeverCharacterResponse> {
            client.get(url(region, "/profile/wow/character/${foreverCharacterSlug(name, surname)}?locale=en_US")) {
                header(BATTLENET_NAMESPACE, foreverProfileNamespace(region))
            }
        }.map { it.toWowForeverCharacter() }
    }

    override suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, String?> {
        logger.debug("getCharacterMedia for $region $realm $character")
        return fetchFromApi<GetWowMediaResponse> {
            client.get(
                url(region, "/profile/wow/character/$realm/${encodedName(character)}/character-media?locale=en_US")
            ) {
                header(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
            }
        }.map { it.asset("avatar") }
    }

    override suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, List<WowEquippedItemRef>> {
        logger.debug("getCharacterEquipment for $region $realm $character")
        return fetchFromApi<GetWowEquipmentResponse> {
            client.get(url(region, "/profile/wow/character/$realm/${encodedName(character)}/equipment?locale=en_US")) {
                header(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
            }
        }.map { it.toEquippedItemRefs() }
    }

    override suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, List<WowSpecialization>> {
        logger.debug("getCharacterSpecialitzation for $region $realm $character")
        return fetchFromApi<GetWowSpecializationsResponse> {
            client.get(
                url(
                    region,
                    "/profile/wow/character/$realm/${encodedName(character)}/specializations?namespace=profile-classic1x-eu&locale=en_US"
                )
            ) {
                header(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
            }
        }.map { it.toActiveSpecializations() }
    }

    override suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, WowStats> {
        logger.debug("getCharacterStats for $region $realm $character")
        return fetchFromApi<GetWowCharacterStatsResponse> {
            client.get(url(region, "/profile/wow/character/$realm/${encodedName(character)}/statistics?locale=en_US")) {
                header(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
            }
        }.map { it.toWowStats() }
    }

    override suspend fun getItemMedia(
        region: String,
        id: Long
    ): Either<ClientError, String?> {
        logger.debug("getItemMedia for $id")
        return fetchFromApi<GetWowMediaResponse> {
            client.get(url(region, "/data/wow/media/item/$id?locale=en_US")) {
                header(BATTLENET_NAMESPACE, STATIC_CLASSIC_NAMESPACE)
            }
        }.map { it.asset("icon") }
    }

    override suspend fun getItem(region: String, id: Long): Either<ClientError, WowItemDetails> {
        logger.debug("getItem for $id")
        return fetchFromApi<GetWowItemResponse> {
            client.get(url(region, "/data/wow/item/$id?locale=en_US")) {
                header(BATTLENET_NAMESPACE, STATIC_CLASSIC_NAMESPACE)
            }
        }.map { it.toWowItemDetails() }
    }

    override suspend fun getRealm(
        region: String,
        id: Long
    ): Either<ClientError, WowRealm> =
        fetchFromApi<GetWowRealmResponse> {
            client.get(url(region, "/data/wow/realm/$id?locale=en_US")) {
                header(BATTLENET_NAMESPACE, DYNAMIC_CLASSIC1X_NAMESPACE)
            }
        }.map { it.toWowRealm() }

    override suspend fun getHardcoreGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GuildRoster> =
        fetchFromApi<GetWowRosterResponse> {
            client.get(
                url(
                    region,
                    "/data/wow/guild/$realm/${slugify(guild)}/roster?namespace=profile-classic1x-eu&locale=en_US"
                )
            ) {
                header(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_EU_NAMESPACE)
            }
        }.map { it.toGuildRoster() }

    override suspend fun getRetailGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GuildRoster> {
        val namespace = "profile-$region"
        return fetchFromApi<GetWowRosterResponse> {
            client.get(
                url(
                    region,
                    "/data/wow/guild/$realm/${slugify(guild)}/roster?namespace=$namespace&locale=en_US"
                )
            ) {
                header(BATTLENET_NAMESPACE, namespace)
            }
        }.map { it.toGuildRoster() }
    }

    override suspend fun getMythicKeystoneProfile(
        region: String,
        realm: String,
        character: String,
        seasonId: Int
    ): Either<ClientError, MythicKeystoneProfile> {
        val namespace = "profile-$region"
        return fetchFromApi<GetWowMythicKeystoneSeasonResponse> {
            client.get(
                url(
                    region,
                    "/profile/wow/character/$realm/${encodedName(character)}/mythic-keystone-profile/season/$seasonId?namespace=$namespace&locale=en_US"
                )
            ) {
                header(BATTLENET_NAMESPACE, namespace)
            }
        }.flatMap { it.toMythicKeystoneProfile() }
    }

    private fun url(region: String, path: String) = (baseURI(region).toString() + URI(path).toString()).lowercase()

    private fun foreverCharacterSlug(name: String, surname: String) = "${encodedName(name)}-${encodedName(surname)}"

    private fun foreverProfileNamespace(region: String) = "profile-forever-$region"

    private fun encodedName(name: String) = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())

    private fun slugify(name: String) = name.trim().replace(Regex("\\s+"), "-")
}
