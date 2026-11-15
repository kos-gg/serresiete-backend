package com.kos.entities

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.BadRequest
import com.kos.common.error.ControllerError
import com.kos.common.error.EntitiesDoNotMatchGame
import com.kos.common.error.InvalidRegion
import com.kos.common.error.MissingSurname
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.datacache.DataCacheService
import com.kos.entities.domain.EntitiesExistResponse
import com.kos.entities.domain.EntityDataResponse
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.belongsTo
import com.kos.entities.domain.hasValidRegion
import com.kos.entities.domain.isBlizzardRegion
import com.kos.entities.domain.missesSurname
import com.kos.entities.guilds.GuildExistsResponse
import com.kos.views.Game

class EntitiesController(
    private val dataCacheService: DataCacheService,
    private val entitiesService: EntitiesService
) {
    suspend fun getEntityData(
        client: String?,
        activities: Set<Activity>,
        maybeSearchRequestAndGame: Pair<EntityRequest, Game>
    ): Either<ControllerError, EntityDataResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.searchEntity)) {
                    Either.Right(dataCacheService.getOrSync(maybeSearchRequestAndGame))
                } else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun exists(
        client: String?,
        activities: Set<Activity>,
        entities: List<EntityRequest>,
        game: Game,
    ): Either<ControllerError, EntitiesExistResponse> = either {
        ensureNotNull(client) { NotAuthorized }
        ensure(activities.contains(Activities.checkEntitiesExist)) { NotEnoughPermissions(client) }
        ensure(entities.all { it.belongsTo(game) }) { EntitiesDoNotMatchGame }
        ensure(entities.all { it.hasValidRegion() }) { InvalidRegion }
        ensure(entities.none { it.missesSurname() }) { MissingSurname }
        entitiesService.exists(entities, game)
    }

    suspend fun guildExists(
        client: String?,
        activities: Set<Activity>,
        name: String,
        region: String,
        realm: String?,
        game: Game,
    ): Either<ControllerError, GuildExistsResponse> = either {
        ensureNotNull(client) { NotAuthorized }
        ensure(activities.contains(Activities.checkEntitiesExist)) { NotEnoughPermissions(client) }
        ensure(isBlizzardRegion(region)) { InvalidRegion }
        when (game) {
            Game.WOW -> {
                val guildRealm = ensureNotNull(realm) { BadRequest("Realm is required to check a wow guild") }
                entitiesService.wowGuildExists(name, region, guildRealm).bind()
            }

            Game.WOW_FOREVER -> {
                ensure(realm == null) { BadRequest("Wow forever guilds have no realm") }
                entitiesService.wowForeverGuildExists(name, region).bind()
            }

            Game.WOW_HC, Game.LOL -> raise(BadRequest("Guild existence can't be checked for $game"))
        }
    }
}