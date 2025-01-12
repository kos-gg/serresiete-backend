package com.kos.views.repository

import com.kos.common.fold
import com.kos.common.getOrThrow
import com.kos.views.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ViewsDatabaseRepository(private val db: Database) : ViewsRepository {

    override suspend fun withState(initialState: List<SimpleView>): ViewsDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Views.batchInsert(initialState) {
                this[Views.id] = it.id
                this[Views.name] = it.name
                this[Views.owner] = it.owner
                this[Views.published] = it.published
                this[Views.game] = it.game.toString()
                this[Views.featured] = it.featured
            }
            initialState.forEach { sv ->
                ViewEntities.batchInsert(sv.entitiesIds) {
                    this[ViewEntities.viewId] = sv.id
                    this[ViewEntities.entityId] = it
                }
            }
        }
        return this
    }

    object Views : Table("views") {
        val id = varchar("id", 48)
        val name = varchar("name", 128)
        val owner = varchar("owner", 48)
        val published = bool("published")
        val game = text("game")
        val featured = bool("featured")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToSimpleView(row: ResultRow): SimpleView {
        return SimpleView(
            row[Views.id],
            row[Views.name],
            row[Views.owner],
            row[Views.published],
            ViewEntities.selectAll().where { ViewEntities.viewId.eq(row[Views.id]) }
                .map { resultRowToViewEntity(it).second },
            Game.fromString(row[Views.game]).getOrThrow(),
            row[Views.featured]
        )
    }

    object ViewEntities : Table("view_entities") {
        val entityId = long("entity_id")
        val viewId = varchar("view_id", 48).references(
            Views.id, onDelete = ReferenceOption.CASCADE
        )
    }

    private fun resultRowToViewEntity(row: ResultRow): Pair<String, Long> = Pair(
        row[ViewEntities.viewId],
        row[ViewEntities.entityId]
    )

    override suspend fun getOwnViews(owner: String): List<SimpleView> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Views.selectAll().where { Views.owner.eq(owner) }.map { resultRowToSimpleView(it) }
        }
    }

    override suspend fun get(id: String): SimpleView? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Views.selectAll().where { Views.id.eq(id) }.map { resultRowToSimpleView(it) }
        }.singleOrNull()
    }

    override suspend fun create(
        id: String,
        name: String,
        owner: String,
        entitiesIds: List<Long>,
        game: Game,
        featured: Boolean,
    ): SimpleView {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Views.insert {
                it[Views.id] = id
                it[Views.name] = name
                it[Views.owner] = owner
                it[published] = true
                it[Views.game] = game.toString()
                it[Views.featured] = featured
            }
            ViewEntities.batchInsert(entitiesIds) {
                this[ViewEntities.viewId] = id
                this[ViewEntities.entityId] = it
            }
        }
        return SimpleView(id, name, owner, true, entitiesIds, game, featured)
    }

    override suspend fun edit(
        id: String,
        name: String,
        published: Boolean,
        entities: List<Long>,
        featured: Boolean
    ): ViewModified {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Views.update({ Views.id.eq(id) }) {
                it[Views.name] = name
                it[Views.published] = published
                it[Views.featured] = featured
            }
            ViewEntities.deleteWhere { viewId.eq(id) }
            ViewEntities.batchInsert(entities) {
                this[ViewEntities.viewId] = id
                this[ViewEntities.entityId] = it
            }
        }
        return ViewModified(id, name, published, entities, featured)
    }

    override suspend fun patch(
        id: String,
        name: String?,
        published: Boolean?,
        entities: List<Long>?,
        featured: Boolean?
    ): ViewPatched {
        newSuspendedTransaction(Dispatchers.IO, db) {
            name?.let { Views.update({ Views.id.eq(id) }) { statement -> statement[Views.name] = it } }
            published?.let { Views.update({ Views.id.eq(id) }) { statement -> statement[Views.published] = it } }
            entities?.let {
                ViewEntities.deleteWhere { viewId.eq(id) }
                ViewEntities.batchInsert(it) { cid ->
                    this[ViewEntities.viewId] = id
                    this[ViewEntities.entityId] = cid
                }
            }
            featured?.let { Views.update({ Views.id.eq(id) }) { statement -> statement[Views.featured] = it } }
        }
        return ViewPatched(id, name, published, entities, featured)
    }

    override suspend fun delete(id: String): Unit {
        newSuspendedTransaction(Dispatchers.IO, db) { Views.deleteWhere { Views.id.eq(id) } }
    }

    override suspend fun getViews(
        game: Game?,
        featured: Boolean,
        page: Int?,
        limit: Int?,
    ): Pair<ViewMetadata, List<SimpleView>> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val baseQuery = Views.selectAll()
            //TODO: we must find a way to retrieve the count in a single query
            val totalRows = baseQuery.count().toInt()

            val featuredCondition = if (featured) Views.featured eq true else null
            val gameCondition = game?.let { Views.game eq it.toString() }

            val queryWithWhere =
                baseQuery.adjustWhere {
                    Op.TRUE
                        .andIfNotNull(featuredCondition)
                        .andIfNotNull(gameCondition)
                }

            val views = limit.fold(
                { queryWithWhere },
                { queryWithWhere.limit(it, offset = ((page ?: 1) - 1).toLong() * it) }
            ).map { resultRowToSimpleView(it) }

            Pair(ViewMetadata(totalRows), views)
        }
    }

    override suspend fun state(): List<SimpleView> {
        return newSuspendedTransaction(Dispatchers.IO, db) { Views.selectAll().map { resultRowToSimpleView(it) } }
    }
}