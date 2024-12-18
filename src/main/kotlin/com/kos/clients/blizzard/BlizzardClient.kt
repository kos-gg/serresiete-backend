package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.domain.*
import com.kos.common.HttpError

interface BlizzardClient {
    suspend fun getCharacterProfile(region: String, realm: String, character: String): Either<HttpError, GetWowCharacterResponse>
    suspend fun getCharacterMedia(region: String, realm: String, character: String): Either<HttpError, GetWowMediaResponse>
    suspend fun getCharacterEquipment(region: String, realm: String, character: String): Either<HttpError, GetWowEquipmentResponse>
    suspend fun getCharacterSpecializations(region: String, realm: String, character: String): Either<HttpError, GetWowSpecializationsResponse>
    suspend fun getCharacterStats(region: String, realm: String, character: String): Either<HttpError, GetWowCharacterStatsResponse>
    suspend fun getItemMedia(region: String, id: Long): Either<HttpError, GetWowMediaResponse>
    suspend fun getItem(region: String, id: Long): Either<HttpError, GetWowItemResponse>
    suspend fun getRealm(region: String, id: Long): Either<HttpError, GetWowRealmResponse>
}