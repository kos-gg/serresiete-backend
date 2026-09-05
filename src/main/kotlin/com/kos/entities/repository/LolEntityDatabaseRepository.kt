package com.kos.entities.repository

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.common.WithState
import com.kos.common.error.RepositoryError
import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntity
import com.kos.entities.domain.LolEntityRequest
import com.kos.views.Game
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException

class LolEntityDatabaseRepository(private val db: Database) :
    GameEntityRepository,
    WithState<List<LolEntity>, LolEntityDatabaseRepository> {

    private object LolEntities : Table("lol_entities") {
        val id = long("id").references(EntitiesDatabaseRepository.Entities.id, onDelete = ReferenceOption.CASCADE)
        val name = text("name")
        val tag = text("tag")
        val puuid = text("puuid")
        val summonerIcon = integer("summoner_icon")
        val summonerLevel = integer("summoner_level")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToEntity(row: ResultRow) = LolEntity(
        row[LolEntities.id],
        row[LolEntities.name],
        row[LolEntities.tag],
        row[LolEntities.puuid],
        row[LolEntities.summonerIcon],
        row[LolEntities.summonerLevel]
    )

    override suspend fun insert(entities: List<InsertEntityRequest>): Either<RepositoryError, List<Entity>> = either {
        val charsToInsert = entities.map { request ->
            ensure(request is LolEnrichedEntityRequest) { RepositoryError("problem inserting $request for LOL") }
            LolEntity(
                selectNextId(db),
                request.name,
                request.tag,
                request.puuid,
                request.summonerIconId,
                request.summonerLevel
            )
        }
        newSuspendedTransaction(Dispatchers.IO, db) {
            transaction {
                try {
                    EntitiesDatabaseRepository.Entities.batchInsert(charsToInsert) {
                        this[EntitiesDatabaseRepository.Entities.id] = it.id
                    }
                    val inserted = LolEntities.batchInsert(charsToInsert) {
                        this[LolEntities.id] = it.id
                        this[LolEntities.name] = it.name
                        this[LolEntities.tag] = it.tag
                        this[LolEntities.puuid] = it.puuid
                        this[LolEntities.summonerIcon] = it.summonerIcon
                        this[LolEntities.summonerLevel] = it.summonerLevel
                    }.map { resultRowToEntity(it) }
                    Either.Right(inserted)
                } catch (e: SQLException) {
                    rollback() //TODO: I don't understand why rollback is not provided by dbQuery.
                    Either.Left(RepositoryError(e.message ?: e.stackTraceToString()))
                }
            }
        }.bind()
    }

    override suspend fun update(id: Long, entity: InsertEntityRequest): Either<RepositoryError, Int> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            when (entity) {
                is LolEnrichedEntityRequest -> Either.Right(LolEntities.update({ LolEntities.id eq id }) {
                    it[name] = entity.name
                    it[tag] = entity.tag
                    it[puuid] = entity.puuid
                    it[summonerIcon] = entity.summonerIconId
                    it[summonerLevel] = entity.summonerLevel
                })

                else -> Either.Left(RepositoryError("problem updating $id: $entity for LOL"))
            }
        }

    override suspend fun get(id: Long): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        LolEntities.selectAll().where { LolEntities.id.eq(id) }.singleOrNull()?.let { resultRowToEntity(it) }
    }

    override suspend fun get(request: EntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        request as LolEntityRequest
        LolEntities.selectAll().where {
            LolEntities.tag.eq(request.tag)
                .and(LolEntities.name.eq(request.name))
        }.map { resultRowToEntity(it) }.singleOrNull()
    }

    override suspend fun get(entity: InsertEntityRequest): Entity? = newSuspendedTransaction(Dispatchers.IO, db) {
        entity as LolEnrichedEntityRequest
        LolEntities.selectAll().where {
            LolEntities.puuid.eq(entity.puuid)
        }.map { resultRowToEntity(it) }.singleOrNull()
    }

    override suspend fun getAll(): List<Entity> = newSuspendedTransaction(Dispatchers.IO, db) {
        LolEntities.selectAll().map { resultRowToEntity(it) }
    }

    override suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            entitiesOlderThanQuery(LolEntities.id, Game.LOL, olderThanMinutes, ::resultRowToEntity, maxEntities)
        }

    override suspend fun state(): List<LolEntity> = newSuspendedTransaction(Dispatchers.IO, db) {
        LolEntities.selectAll().map { resultRowToEntity(it) }
    }

    override suspend fun withState(initialState: List<LolEntity>): LolEntityDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            EntitiesDatabaseRepository.Entities.batchInsert(initialState) {
                this[EntitiesDatabaseRepository.Entities.id] = it.id
            }
            LolEntities.batchInsert(initialState) {
                this[LolEntities.id] = it.id
                this[LolEntities.name] = it.name
                this[LolEntities.tag] = it.tag
                this[LolEntities.puuid] = it.puuid
                this[LolEntities.summonerIcon] = it.summonerIcon
                this[LolEntities.summonerLevel] = it.summonerLevel
            }
        }
        //This needs to be done to consume serial ids. Could be done in a different way but I don't dislike it.
        initialState.forEach { _ -> selectNextId(db) }
        return this
    }
}
