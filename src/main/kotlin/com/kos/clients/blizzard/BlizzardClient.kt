package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.datacache.domain.*
import com.kos.entities.domain.GuildRoster
import com.kos.entities.domain.WowRealm
import com.kos.sources.wowhc.WowEquippedItemRef
import com.kos.sources.wowhc.WowItemDetails
import com.kos.entities.domain.WowCharacter
import com.kos.entities.domain.WowForeverCharacter
import com.kos.sources.wow.MythicKeystoneProfile
import com.kos.sources.wowforever.WowForeverEquippedItem

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

    suspend fun getForeverProfile(
        region: String,
        name: String,
        surname: String
    ): Either<ClientError, WowForeverCharacter>

    suspend fun getForeverEquipment(
        region: String,
        name: String,
        surname: String
    ): Either<ClientError, List<WowForeverEquippedItem>>

    suspend fun getForeverItemMedia(region: String, id: Long): Either<ClientError, String?>

    suspend fun getForeverSpecializations(
        region: String,
        name: String,
        surname: String
    ): Either<ClientError, List<WowForeverSpecialization>>

    suspend fun getForeverSpellMedia(region: String, id: Long): Either<ClientError, String?>

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

    suspend fun getMythicKeystoneProfile(
        region: String,
        realm: String,
        character: String,
        seasonId: Int
    ): Either<ClientError, MythicKeystoneProfile>

}