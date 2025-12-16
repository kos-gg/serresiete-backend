package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.common.error.HttpError

interface BlizzardClient {
    suspend fun getCharacterProfile(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowCharacterResponse>

    suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowMediaResponse>

    suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowEquipmentResponse>

    suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowSpecializationsResponse>

    suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowCharacterStatsResponse>

    suspend fun getItemMedia(region: String, id: Long): Either<HttpError, GetWowMediaResponse>
    suspend fun getItem(region: String, id: Long): Either<HttpError, GetWowItemResponse>
    suspend fun getRealm(region: String, id: Long): Either<HttpError, GetWowRealmResponse>
    suspend fun getGuildRoster(region: String, realm: String, guild: String): Either<HttpError, GetWowRosterResponse>
    suspend fun <T> fetchFromApi(
        path: String,
        namespace: String,
        tokenResponse: TokenResponse,
        parseResponse: (String) -> T
    ): Either<ClientError, T>

    suspend fun getCharacterProfileV2(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterResponse>

    suspend fun getCharacterMediaV2(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowMediaResponse>

    suspend fun getCharacterEquipmentV2(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowEquipmentResponse>

    suspend fun getCharacterSpecializationsV2(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowSpecializationsResponse>

    suspend fun getCharacterStatsV2(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterStatsResponse>

    suspend fun getItemMediaV2(region: String, id: Long): Either<ClientError, GetWowMediaResponse>
    suspend fun getItemV2(region: String, id: Long): Either<ClientError, GetWowItemResponse>
    suspend fun getRealmV2(region: String, id: Long): Either<ClientError, GetWowRealmResponse>
    suspend fun getGuildRosteV2r(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GetWowRosterResponse>
}