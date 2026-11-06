package com.kos.entities.repository

import com.kos.common.WithState
import com.kos.entities.domain.*
import com.kos.views.Game
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowForeverEntityDatabaseRepository(private val db: Database) :
    GameEntityRepository,
    WithState<List<WowForeverEntity>, WowForeverEntityDatabaseRepository> {

    private object WowForeverEntities : Table("wow_forever_entities") {
        val id = long("id").references(EntitiesDatabaseRepository.Entities.id, onDelete = ReferenceOption.CASCADE)
        val name = text("name")
        val surname = text("surname")
        val region = text("region")
        val blizzardId = long("blizzard_id")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToEntity(row: ResultRow) = WowForeverEntity(
        row[WowForeverEntities.id],
        row[WowForeverEntities.name],
        row[WowForeverEntities.surname],
        row[WowForeverEntities.region],
        row[WowForeverEntities.blizzardId]
    )

    private fun byNameSurnameAndRegion(name: String, surname: String, region: String): Op<Boolean> =
        WowForeverEntities.name.eq(name.lowercase())
            .and(WowForeverEntities.surname.eq(surname.lowercase()))
            .and(WowForeverEntities.region.eq(region))

    private suspend fun insertRows(entities: List<WowForeverEntity>): List<Entity> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            EntitiesDatabaseRepository.Entities.batchInsert(entities) {
                this[EntitiesDatabaseRepository.Entities.id] = it.id
            }
            WowForeverEntities.batchInsert(entities) {
                this[WowForeverEntities.id] = it.id
                this[WowForeverEntities.name] = it.name.lowercase()
                this[WowForeverEntities.surname] = it.surname.lowercase()
                this[WowForeverEntities.region] = it.region
                this[WowForeverEntities.blizzardId] = requireNotNull(it.blizzardId) { "missing blizzardId for $it" }
            }.map { resultRowToEntity(it) }
        }

    override suspend fun insert(entities: List<InsertEntityRequest>): List<Entity> =
        insertRows(entities.map { request ->
            require(request is WowForeverEntityRequest) { "problem inserting $request for WOW_FOREVER" }
            request.toEntity(selectNextId(db))
        })

    override suspend fun update(id: Long, entity: InsertEntityRequest): Int =
        newSuspendedTransaction(Dispatchers.IO, db) {
            when (entity) {
                is WowForeverEntityRequest -> WowForeverEntities.update({ WowForeverEntities.id eq id }) {
                    it[name] = entity.name.lowercase()
                    it[surname] = entity.surname.lowercase()
                    it[region] = entity.region
                    entity.blizzardId?.let { blizzardIdValue -> it[blizzardId] = blizzardIdValue }
                }

                else -> throw IllegalArgumentException("problem updating $id: $entity for WOW_FOREVER")
            }
        }

    override suspend fun get(id: Long): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        WowForeverEntities.selectAll().where { WowForeverEntities.id.eq(id) }.singleOrNull()
            ?.let { resultRowToEntity(it) }
    }

    override suspend fun get(request: EntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        request as WowForeverEntityRequest
        WowForeverEntities.selectAll()
            .where { byNameSurnameAndRegion(request.name, request.surname, request.region) }
            .map { resultRowToEntity(it) }
            .singleOrNull()
    }

    override suspend fun get(entity: InsertEntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        entity as WowForeverEntityRequest
        WowForeverEntities.selectAll()
            .where {
                entity.blizzardId?.let { WowForeverEntities.blizzardId.eq(it) }
                    ?: byNameSurnameAndRegion(entity.name, entity.surname, entity.region)
            }
            .map { resultRowToEntity(it) }
            .singleOrNull()
    }

    override suspend fun getAll(): List<Entity> = state()

    override suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            entitiesOlderThanQuery(
                WowForeverEntities.id,
                Game.WOW_FOREVER,
                olderThanMinutes,
                ::resultRowToEntity,
                maxEntities
            )
        }

    override suspend fun state(): List<WowForeverEntity> = newSuspendedTransaction(Dispatchers.IO, db) {
        WowForeverEntities.selectAll().map { resultRowToEntity(it) }
    }

    override suspend fun withState(initialState: List<WowForeverEntity>): WowForeverEntityDatabaseRepository {
        insertRows(initialState)
        initialState.forEach { _ -> selectNextId(db) }
        return this
    }
}
