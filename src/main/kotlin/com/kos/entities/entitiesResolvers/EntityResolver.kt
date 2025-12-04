package com.kos.entities.entitiesResolvers

import arrow.core.Either
import com.kos.common.HttpError
import com.kos.common.split
import com.kos.entities.CreateEntityRequest
import com.kos.entities.EntityWithAlias
import com.kos.entities.ResolvedEntities
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

//TODO - Classes that extend from this need testing suite
interface EntityResolver {
    suspend fun resolve(
        requested: List<CreateEntityRequest>,
        extra: ViewExtraArguments?
    ): Either<HttpError, ResolvedEntities>

    suspend fun getCurrentAndNewEntities(
        repo: EntitiesRepository,
        entities: List<CreateEntityRequest>,
        game: Game
    ): Pair<List<EntityWithAlias>, List<CreateEntityRequest>> = coroutineScope {
        val resolved = entities.asFlow()
            .map { req ->
                async {
                    val existing = repo.get(req, game)
                    if (existing == null) Either.Right(req)
                    else Either.Left(EntityWithAlias(existing, req.alias))
                }
            }
            .buffer(3)
            .toList()
            .awaitAll()

        resolved.split()
    }
}