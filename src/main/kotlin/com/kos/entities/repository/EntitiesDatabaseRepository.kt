package com.kos.entities.repository

import arrow.core.Either
import com.kos.common.error.RepositoryError
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.views.Game
import com.kos.views.repository.ViewsDatabaseRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime

class EntitiesDatabaseRepository(private val db: Database) : EntitiesRepository {

    private val wowRepository = WowEntityDatabaseRepository(db)
    private val wowHardcoreRepository = WowHardcoreEntityDatabaseRepository(db)
    private val lolRepository = LolEntityDatabaseRepository(db)

    private fun repositoryFor(game: Game): GameEntityRepository = when (game) {
        Game.WOW -> wowRepository
        Game.WOW_HC -> wowHardcoreRepository
        Game.LOL -> lolRepository
    }

    override suspend fun withState(initialState: EntitiesState): EntitiesDatabaseRepository {
        wowRepository.withState(initialState.wowEntities)
        wowHardcoreRepository.withState(initialState.wowHardcoreEntities)
        lolRepository.withState(initialState.lolEntities)
        return this
    }

    object Entities : Table("entities") {
        val id = long("id")

        override val primaryKey = PrimaryKey(id)
    }

    override suspend fun insert(
        entities: List<InsertEntityRequest>,
        game: Game
    ): Either<RepositoryError, List<Entity>> = repositoryFor(game).insert(entities)

    override suspend fun update(
        id: Long,
        entity: InsertEntityRequest,
        game: Game
    ): Either<RepositoryError, Int> = repositoryFor(game).update(id, entity)

    override suspend fun get(id: Long, game: Game): Entity? = repositoryFor(game).get(id)

    override suspend fun get(request: EntityRequest, game: Game): Entity? = repositoryFor(game).get(request)

    override suspend fun get(entity: InsertEntityRequest, game: Game): Entity? = repositoryFor(game).get(entity)

    override suspend fun get(game: Game): List<Entity> = repositoryFor(game).getAll()

    override suspend fun getEntitiesOlderThan(game: Game, olderThanMinutes: Long, maxEntities: Int): List<Entity> =
        repositoryFor(game).getOlderThan(olderThanMinutes, maxEntities)

    override suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            ViewsDatabaseRepository.ViewEntities.selectAll()
                .where { ViewsDatabaseRepository.ViewEntities.entityId.eq(id) }
                .map {
                    it[ViewsDatabaseRepository.ViewEntities.viewId]
                }
        }
    }

    override suspend fun delete(id: Long) {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Entities.deleteWhere { Entities.id.eq(id) }
        }
    }

    override suspend fun state(): EntitiesState = EntitiesState(
        wowRepository.state(),
        wowHardcoreRepository.state(),
        lolRepository.state()
    )
}

internal suspend fun selectNextId(db: Database): Long =
    newSuspendedTransaction(Dispatchers.IO, db) {
        TransactionManager.current().exec("""select nextval('entities_ids') as id""") { rs ->
            if (rs.next()) rs.getLong("id")
            else -1
        }
    } ?: -1

internal fun entitiesOlderThanQuery(
    entityIdColumn: Column<Long>,
    game: Game,
    olderThanMinutes: Long,
    mapper: (ResultRow) -> Entity,
    limit: Int
): List<Entity> {
    val caches = DataCacheDatabaseRepository.DataCaches
    val subQuery = caches
        .select(caches.entityId, caches.inserted.max().alias("inserted"))
        .where { caches.game eq game.toString() }
        .groupBy(caches.entityId)
    val subQueryAliased = subQuery.alias("dc")
    val threshold = OffsetDateTime.now().minusMinutes(olderThanMinutes).toString()

    return entityIdColumn.table
        .leftJoin(subQueryAliased, { entityIdColumn }, { subQueryAliased[caches.entityId] })
        .selectAll().where {
            subQueryAliased[caches.inserted].isNull() or
                    (subQueryAliased[caches.inserted] lessEq threshold)
        }
        .orderBy(subQueryAliased[caches.inserted], SortOrder.ASC_NULLS_FIRST)
        .limit(limit)
        .map(mapper)
}
