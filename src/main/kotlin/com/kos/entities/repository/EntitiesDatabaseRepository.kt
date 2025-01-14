package com.kos.entities.repository

import arrow.core.Either
import com.kos.entities.*
import com.kos.common.InsertError
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.repository.EntitiesDatabaseRepository.WowEntities.references
import com.kos.views.Game
import com.kos.views.repository.ViewsDatabaseRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.time.OffsetDateTime

class EntitiesDatabaseRepository(private val db: Database) : EntitiesRepository {

    override suspend fun withState(initialState: EntitiesState): EntitiesDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Entities.batchInsert(initialState.lolEntities) {
                this[Entities.id] = it.id
            }
            Entities.batchInsert(initialState.wowEntities) {
                this[Entities.id] = it.id
            }
            Entities.batchInsert(initialState.wowHardcoreEntities) {
                this[Entities.id] = it.id
            }

            WowEntities.batchInsert(initialState.wowEntities) {
                this[WowEntities.id] = it.id
                this[WowEntities.name] = it.name
                this[WowEntities.region] = it.region
                this[WowEntities.realm] = it.realm
            }
            WowHardcoreEntities.batchInsert(initialState.wowHardcoreEntities) {
                this[WowHardcoreEntities.id] = it.id
                this[WowHardcoreEntities.name] = it.name
                this[WowHardcoreEntities.region] = it.region
                this[WowHardcoreEntities.realm] = it.realm
                //TODO: at some point this should stop being nullable
                this[WowHardcoreEntities.blizzardId] = it.blizzardId ?: -1
            }
            LolEntities.batchInsert(initialState.lolEntities) {
                this[LolEntities.id] = it.id
                this[LolEntities.name] = it.name
                this[LolEntities.tag] = it.tag
                this[LolEntities.puuid] = it.puuid
                this[LolEntities.summonerIcon] = it.summonerIcon
                this[LolEntities.summonerId] = it.summonerId
                this[LolEntities.summonerLevel] = it.summonerLevel
            }
        }
        //This needs to be done to consume serial ids. Could be done in a different way but I don't dislike it.
        initialState.lolEntities.forEach { _ -> selectNextId() }
        initialState.wowEntities.forEach { _ -> selectNextId() }
        initialState.wowHardcoreEntities.forEach { _ -> selectNextId() }
        return this
    }

    object Entities : Table("entities") {
        val id = long("id")

        override val primaryKey = PrimaryKey(id)
    }

    object WowEntities : Table("wow_entities") {
        val id = long("id").references(Entities.id, onDelete = ReferenceOption.CASCADE)
        val name = text("name")
        val realm = text("realm")
        val region = text("region")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToWowEntity(row: ResultRow) = WowEntity(
        row[WowEntities.id],
        row[WowEntities.name],
        row[WowEntities.region],
        row[WowEntities.realm],
        //TODO: at some point this should stop being nullable
        null
    )

    object WowHardcoreEntities : Table("wow_hardcore_entities") {
        val id = long("id").references(Entities.id, onDelete = ReferenceOption.CASCADE)
        val name = text("name")
        val realm = text("realm")
        val region = text("region")
        val blizzardId = long("blizzard_id")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToWowHardcoreEntity(row: ResultRow) = WowEntity(
        row[WowHardcoreEntities.id],
        row[WowHardcoreEntities.name],
        row[WowHardcoreEntities.region],
        row[WowHardcoreEntities.realm],
        row[WowHardcoreEntities.blizzardId]
    )

    object LolEntities : Table("lol_entities") {
        val id = long("id").references(Entities.id, onDelete = ReferenceOption.CASCADE)
        val name = text("name")
        val tag = text("tag")
        val puuid = text("puuid")
        val summonerIcon = integer("summoner_icon")
        val summonerId = text("summoner_id")
        val summonerLevel = integer("summoner_level")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToLolEntity(row: ResultRow) = LolEntity(
        row[LolEntities.id],
        row[LolEntities.name],
        row[LolEntities.tag],
        row[LolEntities.puuid],
        row[LolEntities.summonerIcon],
        row[LolEntities.summonerId],
        row[LolEntities.summonerLevel]
    )

    override suspend fun insert(
        entities: List<InsertEntityRequest>,
        game: Game
    ): Either<InsertError, List<Entity>> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val nextId = selectNextId()
            val charsToInsert: List<Entity> = entities.map {
                when (it) {
                    is WowEntityRequest -> WowEntity(nextId, it.name, it.region, it.realm, 0)
                    is WowEnrichedEntityRequest -> WowEntity(
                        nextId,
                        it.name,
                        it.region,
                        it.realm,
                        it.blizzardId
                    )

                    is LolEnrichedEntityRequest -> LolEntity(
                        nextId,
                        it.name,
                        it.tag,
                        it.puuid,
                        it.summonerIconId,
                        it.summonerId,
                        it.summonerLevel
                    )
                }
            }
            transaction {
                try {
                    Entities.batchInsert(charsToInsert) {
                        this[Entities.id] = it.id
                    }

                    val insertedEntities = when (game) {
                        Game.WOW -> WowEntities.batchInsert(charsToInsert) {
                            when (it) {
                                is WowEntity -> {
                                    this[WowEntities.id] = it.id
                                    this[WowEntities.name] = it.name
                                    this[WowEntities.region] = it.region
                                    this[WowEntities.realm] = it.realm
                                }

                                else -> throw IllegalArgumentException()
                            }
                        }.map { resultRowToWowEntity(it) }

                        Game.LOL -> LolEntities.batchInsert(charsToInsert) {
                            when (it) {
                                is WowEntity -> throw IllegalArgumentException()
                                is LolEntity -> {
                                    this[LolEntities.id] = it.id
                                    this[LolEntities.name] = it.name
                                    this[LolEntities.tag] = it.tag
                                    this[LolEntities.puuid] = it.puuid
                                    this[LolEntities.summonerIcon] = it.summonerIcon
                                    this[LolEntities.summonerId] = it.summonerId
                                    this[LolEntities.summonerLevel] = it.summonerLevel
                                }
                            }
                        }.map { resultRowToLolEntity(it) }

                        Game.WOW_HC -> WowHardcoreEntities.batchInsert(charsToInsert) {
                            when (it) {
                                is WowEntity -> {
                                    this[WowHardcoreEntities.id] = it.id
                                    this[WowHardcoreEntities.name] = it.name
                                    this[WowHardcoreEntities.region] = it.region
                                    this[WowHardcoreEntities.realm] = it.realm
                                    //TODO: at some point this should stop being nullable
                                    this[WowHardcoreEntities.blizzardId] = it.blizzardId ?: -1
                                }

                                else -> throw IllegalArgumentException()
                            }
                        }.map { resultRowToWowHardcoreEntity(it) }
                    }
                    Either.Right(insertedEntities)
                } catch (e: SQLException) {
                    rollback() //TODO: I don't understand why rollback is not provided by dbQuery.
                    Either.Left(InsertError(e.message ?: e.stackTraceToString()))
                } catch (e: IllegalArgumentException) {
                    rollback() //TODO: I don't understand why rollback is not provided by dbQuery.
                    Either.Left(InsertError(e.message ?: e.stackTraceToString()))
                }
            }
        }
    }

    override suspend fun update(
        id: Long,
        entity: InsertEntityRequest,
        game: Game
    ): Either<InsertError, Int> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.LOL -> {
                    when (entity) {
                        is LolEnrichedEntityRequest -> {
                            Either.Right(LolEntities.update({ LolEntities.id eq id }) {
                                it[name] = entity.name
                                it[tag] = entity.tag
                                it[puuid] = entity.puuid
                                it[summonerIcon] = entity.summonerIconId
                                it[summonerId] = entity.summonerId
                                it[summonerLevel] = entity.summonerLevel
                            })
                        }

                        else -> Either.Left(InsertError("problem updating $id: $entity for $game"))
                    }
                }

                Game.WOW -> when (entity) {
                    is WowEntityRequest -> {
                        Either.Right(WowEntities.update({ WowEntities.id eq id }) {
                            it[name] = entity.name
                            it[region] = entity.region
                            it[realm] = entity.realm
                        })
                    }

                    else -> Either.Left(InsertError("problem updating $id: $entity for $game"))
                }

                Game.WOW_HC -> when (entity) {
                    is WowEntityRequest -> {
                        Either.Right(WowHardcoreEntities.update({ WowHardcoreEntities.id eq id }) {
                            it[name] = entity.name
                            it[region] = entity.region
                            it[realm] = entity.realm
                        })
                    }

                    else -> Either.Left(InsertError("problem updating $id: $entity for $game"))
                }
            }
        }
    }

    override suspend fun get(id: Long, game: Game): Entity? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowEntities.selectAll().where { WowEntities.id.eq(id) }.singleOrNull()?.let {
                    resultRowToWowEntity(it)
                }

                Game.LOL -> LolEntities.selectAll().where { LolEntities.id.eq(id) }.singleOrNull()?.let {
                    resultRowToLolEntity(it)
                }

                Game.WOW_HC -> WowHardcoreEntities.selectAll().where { WowHardcoreEntities.id.eq(id) }
                    .singleOrNull()?.let {
                        resultRowToWowHardcoreEntity(it)
                    }
            }
        }
    }

    override suspend fun get(request: CreateEntityRequest, game: Game): Entity? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> {
                    request as WowEntityRequest
                    WowEntities.selectAll().where {
                        WowEntities.name.eq(request.name)
                            .and(WowEntities.realm.eq(request.realm))
                            .and(WowEntities.region.eq(request.region))
                    }.map { resultRowToWowEntity(it) }
                }

                Game.LOL -> {
                    request as LolEntityRequest
                    LolEntities.selectAll().where {
                        LolEntities.tag.eq(request.tag)
                            .and(LolEntities.name.eq(request.name))
                    }.map { resultRowToLolEntity(it) }
                }

                Game.WOW_HC -> {
                    request as WowEntityRequest
                    WowHardcoreEntities.selectAll().where {
                        WowHardcoreEntities.name.eq(request.name)
                            .and(WowHardcoreEntities.realm.eq(request.realm))
                            .and(WowHardcoreEntities.region.eq(request.region))
                    }.map { resultRowToWowHardcoreEntity(it) }
                }
            }.singleOrNull()
        }
    }

    override suspend fun get(entity: InsertEntityRequest, game: Game): Entity? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> {
                    entity as WowEntityRequest
                    WowEntities.selectAll().where {
                        WowEntities.name.eq(entity.name)
                            .and(WowEntities.realm.eq(entity.realm))
                            .and(WowEntities.region.eq(entity.region))
                    }.map { resultRowToWowEntity(it) }
                }

                Game.LOL -> {
                    entity as LolEnrichedEntityRequest
                    LolEntities.selectAll().where {
                        LolEntities.puuid.eq(entity.puuid)
                            .and(LolEntities.summonerId.eq(entity.summonerId))
                    }.map { resultRowToLolEntity(it) }
                }

                Game.WOW_HC -> {
                    entity as WowEntityRequest
                    WowHardcoreEntities.selectAll().where {
                        WowHardcoreEntities.name.eq(entity.name)
                            .and(WowHardcoreEntities.realm.eq(entity.realm))
                            .and(WowHardcoreEntities.region.eq(entity.region))
                    }.map { resultRowToWowHardcoreEntity(it) }
                }
            }
        }.singleOrNull()
    }

    override suspend fun get(game: Game): List<Entity> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowEntities.selectAll().map { resultRowToWowEntity(it) }
                Game.LOL -> LolEntities.selectAll().map { resultRowToLolEntity(it) }
                Game.WOW_HC -> WowHardcoreEntities.selectAll().map { resultRowToWowHardcoreEntity(it) }
            }
        }

    override suspend fun getEntitiesToSync(game: Game, olderThanMinutes: Long): List<Entity> {

        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowEntities.selectAll().map { resultRowToWowEntity(it) }
                Game.LOL -> {
                    val subQuery = DataCacheDatabaseRepository.DataCaches
                        .select(
                            DataCacheDatabaseRepository.DataCaches.entityId,
                            DataCacheDatabaseRepository.DataCaches.inserted.max().alias("inserted")
                        )
                        .groupBy(DataCacheDatabaseRepository.DataCaches.entityId)

                    val subQueryAliased = subQuery.alias("dc")

                    val thirtyMinutesAgo = OffsetDateTime.now().minusMinutes(olderThanMinutes).toString()
                    LolEntities
                        .leftJoin(
                            subQueryAliased,
                            { id },
                            { subQueryAliased[DataCacheDatabaseRepository.DataCaches.entityId] })
                        .selectAll().where {
                            (subQueryAliased[DataCacheDatabaseRepository.DataCaches.inserted].isNull()) or
                                    (subQueryAliased[DataCacheDatabaseRepository.DataCaches.inserted] lessEq thirtyMinutesAgo)
                        }
                        .map { resultRowToLolEntity(it) }
                }

                Game.WOW_HC -> WowHardcoreEntities.selectAll().map { resultRowToWowHardcoreEntity(it) }
            }
        }
    }

    override suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            ViewsDatabaseRepository.ViewEntities.selectAll()
                .where { ViewsDatabaseRepository.ViewEntities.entityId.eq(id) }
                .map {
                    it[ViewsDatabaseRepository.ViewEntities.viewId]
                }
        }
    }

    override suspend fun delete(id: Long, game: Game) {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            when (game) {
                Game.WOW -> WowEntities.deleteWhere { WowEntities.id.eq(id) }
                Game.LOL -> LolEntities.deleteWhere { LolEntities.id.eq(id) }
                Game.WOW_HC -> WowHardcoreEntities.deleteWhere { WowHardcoreEntities.id.eq(id) }
            }

            //TODO: this operation will be removed upon having parent character table implemented
            ViewsDatabaseRepository.ViewEntities.deleteWhere { entityId.eq(id) }
        }
    }


    override suspend fun state(): EntitiesState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            EntitiesState(
                WowEntities.selectAll().map { resultRowToWowEntity(it) },
                WowHardcoreEntities.selectAll().map { resultRowToWowHardcoreEntity(it) },
                LolEntities.selectAll().map { resultRowToLolEntity(it) }
            )
        }
    }

    private suspend fun selectNextId(): Long =
        newSuspendedTransaction(Dispatchers.IO, db) {
            TransactionManager.current().exec("""select nextval('entities_ids') as id""") { rs ->
                if (rs.next()) rs.getLong("id")
                else -1
            }
        } ?: -1
}