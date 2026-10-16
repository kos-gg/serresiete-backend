package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.datacache.domain.WowCharacter
import com.kos.datacache.domain.WowEquippedItemRef
import com.kos.datacache.domain.WowItemDetails
import com.kos.datacache.domain.WowSpecialization
import com.kos.datacache.domain.WowStats
import com.kos.entities.domain.GuildRoster
import com.kos.entities.domain.WowRealm

interface BlizzardClient {
    suspend fun getClassicProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, WowCharacter>

    suspend fun getRetailProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, WowCharacter>

    suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, String?>

    suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, List<WowEquippedItemRef>>

    suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, List<WowSpecialization>>

    suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, WowStats>

    suspend fun getItemMedia(region: String, id: Long): Either<ClientError, String?>
    suspend fun getItem(region: String, id: Long): Either<ClientError, WowItemDetails>
    suspend fun getRealm(region: String, id: Long): Either<ClientError, WowRealm>
    suspend fun getHardcoreGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GuildRoster>

    suspend fun getRetailGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GuildRoster>

}