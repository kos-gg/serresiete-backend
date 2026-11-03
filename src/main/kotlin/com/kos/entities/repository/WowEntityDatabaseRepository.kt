package com.kos.entities.repository

import com.kos.common.WithState
import com.kos.entities.domain.*
import com.kos.views.Game
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowEntityDatabaseRepository(private val db: Database) :
    GameEntityRepository,
    WithState<List<WowEntity>, WowEntityDatabaseRepository> {

    private object WowEntities : Table("wow_entities") {
        val id = long("id").references(EntitiesDatabaseRepository.Entities.id, onDelete = ReferenceOption.CASCADE)
        val name = text("name")
        val realm = text("realm")
        val region = text("region")
        val blizzardId = long("blizzard_id").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToEntity(row: ResultRow) = WowEntity(
        row[WowEntities.id],
        row[WowEntities.name],
        row[WowEntities.region],
        row[WowEntities.realm],
        row[WowEntities.blizzardId]
    )

    override suspend fun insert(entities: List<InsertEntityRequest>): List<Entity> {
        val charsToInsert = entities.map { request ->
            require(request is WowEntityRequest) { "problem inserting $request for WOW" }
            WowEntity(selectNextId(db), request.name.lowercase(), request.region, request.realm, request.blizzardId)
        }
        return newSuspendedTransaction(Dispatchers.IO, db) {
            EntitiesDatabaseRepository.Entities.batchInsert(charsToInsert) {
                this[EntitiesDatabaseRepository.Entities.id] = it.id
            }
            WowEntities.batchInsert(charsToInsert) {
                this[WowEntities.id] = it.id
                this[WowEntities.name] = it.name
                this[WowEntities.region] = it.region
                this[WowEntities.realm] = it.realm
                this[WowEntities.blizzardId] = it.blizzardId
            }.map { resultRowToEntity(it) }
        }
    }

    override suspend fun update(id: Long, entity: InsertEntityRequest): Int =
        newSuspendedTransaction(Dispatchers.IO, db) {
            when (entity) {
                is WowEntityRequest -> WowEntities.update({ WowEntities.id eq id }) {
                    it[name] = entity.name.lowercase()
                    it[region] = entity.region
                    it[realm] = entity.realm
                    it[blizzardId] = entity.blizzardId
                }

                else -> throw IllegalArgumentException("problem updating $id: $entity for WOW")
            }
        }

    override suspend fun get(id: Long): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        WowEntities.selectAll().where { WowEntities.id.eq(id) }.singleOrNull()?.let { resultRowToEntity(it) }
    }

    override suspend fun get(request: EntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        request as WowEntityRequest
        WowEntities.selectAll().where {
            WowEntities.name.eq(request.name.lowercase())
                .and(WowEntities.realm.eq(request.realm))
                .and(WowEntities.region.eq(request.region))
        }.map { resultRowToEntity(it) }.singleOrNull()
    }

    override suspend fun get(entity: InsertEntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        entity as WowEntityRequest
        val query = if (entity.blizzardId != null) {
            WowEntities.selectAll().where { WowEntities.blizzardId.eq(entity.blizzardId) }
        } else {
            WowEntities.selectAll().where {
                WowEntities.name.eq(entity.name.lowercase())
                    .and(WowEntities.realm.eq(entity.realm))
                    .and(WowEntities.region.eq(entity.region))
            }
        }
        query.map { resultRowToEntity(it) }.singleOrNull()
    }

    override suspend fun getAll(): List<Entity> = newSuspendedTransaction(Dispatchers.IO, db) {
        WowEntities.selectAll().map { resultRowToEntity(it) }
    }

    override suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            entitiesOlderThanQuery(WowEntities.id, Game.WOW, olderThanMinutes, ::resultRowToEntity, maxEntities)
        }

    override suspend fun state(): List<WowEntity> = newSuspendedTransaction(Dispatchers.IO, db) {
        WowEntities.selectAll().map { resultRowToEntity(it) }
    }

    override suspend fun withState(initialState: List<WowEntity>): WowEntityDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            EntitiesDatabaseRepository.Entities.batchInsert(initialState) {
                this[EntitiesDatabaseRepository.Entities.id] = it.id
            }
            WowEntities.batchInsert(initialState) {
                this[WowEntities.id] = it.id
                this[WowEntities.name] = it.name.lowercase()
                this[WowEntities.region] = it.region
                this[WowEntities.realm] = it.realm
                this[WowEntities.blizzardId] = it.blizzardId
            }
        }
        //This needs to be done to consume serial ids. Could be done in a different way but I don't dislike it.
        initialState.forEach { _ -> selectNextId(db) }
        return this
    }
}
