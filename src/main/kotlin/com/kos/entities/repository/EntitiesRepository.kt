package com.kos.entities.repository

import arrow.core.Either
import com.kos.common.WithState
import com.kos.entities.*
import com.kos.views.Game

data class EntitiesState(
    val wowEntities: List<WowEntity>,
    val wowHardcoreEntities: List<WowEntity>,
    val lolEntities: List<LolEntity>
)

interface EntitiesRepository : WithState<EntitiesState, EntitiesRepository> {

    //TODO: insert should be on conflict do nothing so we can avoid the select all + diff on service
    suspend fun insert(entities: List<InsertEntityRequest>, game: Game): Either<InsertError, List<Entity>>
    suspend fun update(id: Long, entity: InsertEntityRequest, game: Game): Either<InsertError, Int>
    suspend fun get(id: Long, game: Game): Entity?
    suspend fun get(request: CreateEntityRequest, game: Game): Entity?
    suspend fun get(game: Game): List<Entity>
    suspend fun getEntitiesToSync(game: Game, olderThanMinutes: Long): List<Entity>
    suspend fun get(entity: InsertEntityRequest, game: Game): Entity?
    suspend fun getViewsFromEntity(id: Long, game: Game?): List<String>
    suspend fun delete(id: Long): Unit
}