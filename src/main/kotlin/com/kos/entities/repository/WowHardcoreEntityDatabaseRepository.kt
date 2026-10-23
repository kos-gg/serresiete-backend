package com.kos.entities.repository

import com.kos.common.WithState
import com.kos.entities.domain.*
import com.kos.views.Game
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowHardcoreEntityDatabaseRepository(private val db: Database) :
    GameEntityRepository,
    WithState<List<WowEntity>, WowHardcoreEntityDatabaseRepository> {

    private object WowHardcoreEntities : Table("wow_hardcore_entities") {
        val id = long("id").references(EntitiesDatabaseRepository.Entities.id, onDelete = ReferenceOption.CASCADE)
        val name = text("name")
        val realm = text("realm")
        val region = text("region")
        val blizzardId = long("blizzard_id")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToEntity(row: ResultRow) = WowEntity(
        row[WowHardcoreEntities.id],
        row[WowHardcoreEntities.name],
        row[WowHardcoreEntities.region],
        row[WowHardcoreEntities.realm],
        row[WowHardcoreEntities.blizzardId]
    )

    override suspend fun insert(entities: List<InsertEntityRequest>): List<Entity> {
        val charsToInsert = entities.map { request ->
            require(request is WowEntityRequest) { "problem inserting $request for WOW_HC" }
            WowEntity(
                selectNextId(db),
                request.name.lowercase(),
                request.region,
                request.realm,
                request.blizzardId ?: 0
            )
        }
        return newSuspendedTransaction(Dispatchers.IO, db) {
            EntitiesDatabaseRepository.Entities.batchInsert(charsToInsert) {
                this[EntitiesDatabaseRepository.Entities.id] = it.id
            }
            WowHardcoreEntities.batchInsert(charsToInsert) {
                this[WowHardcoreEntities.id] = it.id
                this[WowHardcoreEntities.name] = it.name
                this[WowHardcoreEntities.region] = it.region
                this[WowHardcoreEntities.realm] = it.realm
                this[WowHardcoreEntities.blizzardId] = it.blizzardId ?: 0
            }.map { resultRowToEntity(it) }
        }
    }

    override suspend fun update(id: Long, entity: InsertEntityRequest): Int =
        newSuspendedTransaction(Dispatchers.IO, db) {
            when (entity) {
                is WowEntityRequest -> WowHardcoreEntities.update({ WowHardcoreEntities.id eq id }) {
                    it[name] = entity.name.lowercase()
                    it[region] = entity.region
                    it[realm] = entity.realm
                    entity.blizzardId?.let { blizzardIdValue -> it[blizzardId] = blizzardIdValue }
                }

                else -> throw IllegalArgumentException("problem updating $id: $entity for WOW_HC")
            }
        }

    override suspend fun get(id: Long): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        WowHardcoreEntities.selectAll().where { WowHardcoreEntities.id.eq(id) }.singleOrNull()
            ?.let { resultRowToEntity(it) }
    }

    override suspend fun get(request: EntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        request as WowEntityRequest
        WowHardcoreEntities.selectAll().where {
            WowHardcoreEntities.name.eq(request.name.lowercase())
                .and(WowHardcoreEntities.realm.eq(request.realm))
                .and(WowHardcoreEntities.region.eq(request.region))
        }.map { resultRowToEntity(it) }.singleOrNull()
    }

    override suspend fun get(entity: InsertEntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        entity as WowEntityRequest
        val query = if (entity.blizzardId != null) {
            WowHardcoreEntities.selectAll().where { WowHardcoreEntities.blizzardId.eq(entity.blizzardId) }
        } else {
            WowHardcoreEntities.selectAll().where {
                WowHardcoreEntities.name.eq(entity.name.lowercase())
                    .and(WowHardcoreEntities.realm.eq(entity.realm))
                    .and(WowHardcoreEntities.region.eq(entity.region))
            }
        }
        query.map { resultRowToEntity(it) }.singleOrNull()
    }

    override suspend fun getAll(): List<Entity> = newSuspendedTransaction(Dispatchers.IO, db) {
        WowHardcoreEntities.selectAll().map { resultRowToEntity(it) }
    }

    override suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            entitiesOlderThanQuery(
                WowHardcoreEntities.id,
                Game.WOW_HC,
                olderThanMinutes,
                ::resultRowToEntity,
                maxEntities
            )
        }

    override suspend fun state(): List<WowEntity> = newSuspendedTransaction(Dispatchers.IO, db) {
        WowHardcoreEntities.selectAll().map { resultRowToEntity(it) }
    }

    override suspend fun withState(initialState: List<WowEntity>): WowHardcoreEntityDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            EntitiesDatabaseRepository.Entities.batchInsert(initialState) {
                this[EntitiesDatabaseRepository.Entities.id] = it.id
            }
            WowHardcoreEntities.batchInsert(initialState) {
                this[WowHardcoreEntities.id] = it.id
                this[WowHardcoreEntities.name] = it.name.lowercase()
                this[WowHardcoreEntities.region] = it.region
                this[WowHardcoreEntities.realm] = it.realm
                //TODO: at some point this should stop being nullable
                this[WowHardcoreEntities.blizzardId] = it.blizzardId ?: -1
            }
        }
        //This needs to be done to consume serial ids. Could be done in a different way but I don't dislike it.
        initialState.forEach { _ -> selectNextId(db) }
        return this
    }
}
