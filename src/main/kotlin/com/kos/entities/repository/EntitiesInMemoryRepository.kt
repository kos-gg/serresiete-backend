package com.kos.entities.repository

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.InsertError
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.domain.CreateEntityRequest
import com.kos.entities.domain.Entity
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntity
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.WowEnrichedEntityRequest
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import java.time.OffsetDateTime

class EntitiesInMemoryRepository(
    private val dataCacheRepository: DataCacheInMemoryRepository = DataCacheInMemoryRepository(),
    private val viewsRepository: ViewsInMemoryRepository = ViewsInMemoryRepository()
) :
    EntitiesRepository,
    InMemoryRepository {
    private val wowEntities: MutableList<WowEntity> = mutableListOf()
    private val wowHardcoreEntities: MutableList<WowEntity> = mutableListOf()
    private val lolEntities: MutableList<LolEntity> = mutableListOf()

    private fun nextId(): Long {
        val allIds = wowEntities.map { it.id } + lolEntities.map { it.id } + wowHardcoreEntities.map { it.id }
        return if (allIds.isEmpty()) 1
        else allIds.maxBy { it } + 1
    }

    override suspend fun insert(
        entities: List<InsertEntityRequest>,
        game: Game
    ): Either<InsertError, List<Entity>> {
        val wowInitialEntities = this.wowEntities.toList()
        val wowHardcoreInitialEntities = this.wowHardcoreEntities.toList()
        val lolInitialEntities = this.lolEntities.toList()
        when (game) {
            Game.WOW -> {
                val inserted = entities.fold(listOf<Entity>()) { acc, it ->
                    when (it) {
                        is WowEntityRequest -> {
                            if (this.wowEntities.any { entity -> it.same(entity) }) {
                                this.wowEntities.clear()
                                this.wowEntities.addAll(wowInitialEntities)
                                return Either.Left(InsertError("Error inserting entity $it"))
                            }
                            val entity = it.toEntity(nextId())
                            this.wowEntities.add(entity)
                            acc + entity
                        }

                        is LolEnrichedEntityRequest, is WowEnrichedEntityRequest -> {
                            this.wowEntities.clear()
                            this.wowEntities.addAll(wowInitialEntities)
                            return Either.Left(InsertError("Error inserting entity $it"))
                        }
                    }
                }
                return Either.Right(inserted)
            }

            Game.LOL -> {
                val inserted = entities.fold(listOf<Entity>()) { acc, it ->
                    when (it) {
                        is WowEntityRequest, is WowEnrichedEntityRequest -> {
                            this.lolEntities.clear()
                            this.lolEntities.addAll(lolInitialEntities)
                            return Either.Left(InsertError("Error inserting chracter $it"))
                        }

                        is LolEnrichedEntityRequest -> {
                            if (this.lolEntities.any { entity -> it.same(entity) }) {
                                this.lolEntities.clear()
                                this.lolEntities.addAll(lolInitialEntities)
                                return Either.Left(InsertError("Error inserting chracter $it"))
                            }
                            val entity = it.toEntity(nextId())
                            this.lolEntities.add(entity)
                            acc + entity
                        }
                    }
                }
                return Either.Right(inserted)
            }

            Game.WOW_HC -> {
                val inserted = entities.fold(listOf<Entity>()) { acc, it ->
                    when (it) {
                        is WowEnrichedEntityRequest -> {
                            if (this.wowHardcoreEntities.any { entity -> it.same(entity) }) {
                                this.wowHardcoreEntities.clear()
                                this.wowHardcoreEntities.addAll(wowHardcoreInitialEntities)
                                return Either.Left(InsertError("Error inserting entity $it"))
                            }
                            val entity = it.toEntity(nextId())
                            this.wowHardcoreEntities.add(entity)
                            acc + entity
                        }

                        is LolEnrichedEntityRequest, is WowEntityRequest -> {
                            this.wowHardcoreEntities.clear()
                            this.wowHardcoreEntities.addAll(wowInitialEntities)
                            return Either.Left(InsertError("Error inserting entity $it"))
                        }
                    }
                }
                return Either.Right(inserted)
            }
        }
    }

    override suspend fun update(
        id: Long,
        entity: InsertEntityRequest,
        game: Game
    ): Either<InsertError, Int> {
        return when (game) {
            Game.LOL -> when (entity) {
                is LolEnrichedEntityRequest -> {
                    val index = lolEntities.indexOfFirst { it.id == id }
                    lolEntities.removeAt(index)
                    val c = LolEntity(
                        id,
                        entity.name,
                        entity.tag,
                        entity.puuid,
                        entity.summonerIconId,
                        entity.summonerLevel
                    )
                    lolEntities.add(index, c)
                    Either.Right(1)
                }

                else -> Either.Left(InsertError("error updating $id $entity for $game"))
            }

            Game.WOW -> when (entity) {
                is WowEntityRequest -> {
                    val index = wowEntities.indexOfFirst { it.id == id }
                    wowEntities.removeAt(index)
                    val c = WowEntity(
                        id,
                        entity.name,
                        entity.region,
                        entity.realm,
                        null
                    )
                    wowEntities.add(index, c)
                    Either.Right(1)
                }

                else -> Either.Left(InsertError("error updating $id $entity for $game"))
            }

            Game.WOW_HC -> when (entity) {
                //TODO: use enriched, no need to actualInsertedCharacter
                is WowEntityRequest -> {
                    val index = wowHardcoreEntities.indexOfFirst { it.id == id }
                    val actualInsertedCharacter = wowHardcoreEntities[index]
                    wowHardcoreEntities.removeAt(index)
                    val c = WowEntity(
                        id,
                        entity.name,
                        entity.region,
                        entity.realm,
                        actualInsertedCharacter.blizzardId
                    )
                    wowHardcoreEntities.add(index, c)
                    Either.Right(1)
                }

                else -> Either.Left(InsertError("error updating $id $entity for $game"))
            }
        }
    }

    override suspend fun get(request: CreateEntityRequest, game: Game): Entity? =
        when (game) {
            Game.WOW -> wowEntities.find {
                request as WowEntityRequest
                it.name == request.name &&
                        it.realm == request.realm &&
                        it.region == request.region
            }

            Game.LOL -> lolEntities.find {
                request as LolEntityRequest
                it.name == request.name &&
                        it.tag == request.tag
            }

            Game.WOW_HC -> wowHardcoreEntities.find {
                request as WowEntityRequest
                it.name == request.name &&
                        it.realm == request.realm &&
                        it.region == request.region
            }
        }

    override suspend fun get(id: Long, game: Game): Entity? =
        when (game) {
            Game.WOW -> wowEntities.find { it.id == id }
            Game.LOL -> lolEntities.find { it.id == id }
            Game.WOW_HC -> wowHardcoreEntities.find { it.id == id }
        }

    override suspend fun get(game: Game): List<Entity> =
        when (game) {
            Game.WOW -> wowEntities
            Game.LOL -> lolEntities
            Game.WOW_HC -> wowHardcoreEntities
        }

    override suspend fun get(entity: InsertEntityRequest, game: Game): Entity? {
        return when (game) {
            Game.WOW -> wowEntities.find { entity.same(it) }
            Game.LOL -> lolEntities.find { entity.same(it) }
            Game.WOW_HC -> wowHardcoreEntities.find { entity.same(it) }
        }
    }

    override suspend fun getEntitiesToSync(game: Game, olderThanMinutes: Long): List<Entity> {
        val now = OffsetDateTime.now()

        return when (game) {
            Game.WOW -> wowEntities
            Game.WOW_HC -> wowHardcoreEntities
            Game.LOL -> {
                lolEntities.filter { entity ->
                    val newestCachedRecord = dataCacheRepository.get(entity.id).maxByOrNull { it.inserted }
                    newestCachedRecord == null || newestCachedRecord.inserted.isBefore(now.minusMinutes(olderThanMinutes))
                }
            }
        }
    }

    override suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> {
        return viewsRepository.getViews(game, false, null, null)
            .second
            .filter { id in it.entitiesIds }
            .map { it.id }
    }

    override suspend fun delete(id: Long) {
        val wowIndex = wowEntities.indexOfFirst { it.id == id }
        if (wowIndex != -1) {
            wowEntities.removeAt(wowIndex)
        }

        val lolIndex = lolEntities.indexOfFirst { it.id == id }
        if (lolIndex != -1) {
            lolEntities.removeAt(lolIndex)
        }

        val wowHardcoreIndex = wowHardcoreEntities.indexOfFirst { it.id == id }
        if (wowHardcoreIndex != -1) {
            wowHardcoreEntities.removeAt(wowHardcoreIndex)
        }
    }


    override suspend fun state(): EntitiesState {
        return EntitiesState(wowEntities, wowHardcoreEntities, lolEntities)
    }

    override suspend fun withState(initialState: EntitiesState): EntitiesInMemoryRepository {
        wowEntities.addAll(initialState.wowEntities)
        wowHardcoreEntities.addAll(initialState.wowHardcoreEntities)
        lolEntities.addAll(initialState.lolEntities)
        return this
    }

    override fun clear() {
        wowEntities.clear()
        wowHardcoreEntities.clear()
        lolEntities.clear()
        dataCacheRepository.clear()
    }
}