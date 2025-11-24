package com.kos.views.repository

import com.kos.common.fold
import com.kos.common.getOrThrow
import com.kos.entities.repository.EntitiesDatabaseRepository.Entities
import com.kos.views.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ViewsDatabaseRepository(private val db: Database) : ViewsRepository {

    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(ViewExtraArguments::class) {
                subclass(WowHardcoreExtraArguments::class, WowHardcoreExtraArguments.serializer())
                subclass(WowExtraArguments::class, WowExtraArguments.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun withState(initialState: ViewsState): ViewsDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Views.batchInsert(initialState.views) {
                this[Views.id] = it.id
                this[Views.name] = it.name
                this[Views.owner] = it.owner
                this[Views.published] = it.published
                this[Views.game] = it.game.toString()
                this[Views.featured] = it.featured
            }
            ViewEntities.batchInsert(initialState.viewEntities) {
                this[ViewEntities.viewId] = it.viewId
                this[ViewEntities.entityId] = it.entityId
                this[ViewEntities.alias] = it.alias
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
        val extraArguments = text("extra_arguments").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToSimpleView(row: ResultRow): SimpleView {
        return SimpleView(
            row[Views.id],
            row[Views.name],
            row[Views.owner],
            row[Views.published],
            ViewEntities.selectAll().where { ViewEntities.viewId.eq(row[Views.id]) }
                .map { resultRowToViewEntity(it).entityId },
            Game.fromString(row[Views.game]).getOrThrow(),
            row[Views.featured],
            row[Views.extraArguments]?.let { json.decodeFromString<ViewExtraArguments>(it) }
        )
    }

    object ViewEntities : Table("view_entities") {
        val entityId = long("entity_id").references(
            Entities.id, onDelete = ReferenceOption.CASCADE
        )
        val viewId = varchar("view_id", 48).references(
            Views.id, onDelete = ReferenceOption.CASCADE
        )
        val alias = varchar("alias", 48).nullable()
    }

    private fun resultRowToViewEntity(row: ResultRow): ViewEntity = ViewEntity(
        row[ViewEntities.entityId],
        row[ViewEntities.viewId],
        row[ViewEntities.alias]
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
        entitiesIds: List<entityIdWithAlias>,
        game: Game,
        featured: Boolean,
        extraArguments: ViewExtraArguments?,
    ): SimpleView {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Views.insert {
                it[Views.id] = id
                it[Views.name] = name
                it[Views.owner] = owner
                it[published] = true
                it[Views.game] = game.toString()
                it[Views.featured] = featured
                it[Views.extraArguments] = extraArguments?.let { ea -> json.encodeToString<ViewExtraArguments>(ea) }
            }
            associateEntitiesIdsToViewQuery(entitiesIds, id)
        }
        return SimpleView(id, name, owner, true, entitiesIds.map { it.first }, game, featured, extraArguments)
    }

    override suspend fun edit(
        id: String,
        name: String,
        published: Boolean,
        entities: List<entityIdWithAlias>,
        featured: Boolean
    ): ViewModified {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Views.update({ Views.id.eq(id) }) {
                it[Views.name] = name
                it[Views.published] = published
                it[Views.featured] = featured
            }
            ViewEntities.deleteWhere { viewId.eq(id) }
            associateEntitiesIdsToViewQuery(entities, id)
        }
        return ViewModified(id, name, published, entities.map { it.first }, featured)
    }

    override suspend fun patch(
        id: String,
        name: String?,
        published: Boolean?,
        entities: List<entityIdWithAlias>?,
        featured: Boolean?
    ): ViewPatched {
        newSuspendedTransaction(Dispatchers.IO, db) {
            name?.let { Views.update({ Views.id.eq(id) }) { statement -> statement[Views.name] = it } }
            published?.let { Views.update({ Views.id.eq(id) }) { statement -> statement[Views.published] = it } }
            entities?.let {
                ViewEntities.deleteWhere { viewId.eq(id) }
                associateEntitiesIdsToViewQuery(it, id)
            }
            featured?.let { Views.update({ Views.id.eq(id) }) { statement -> statement[Views.featured] = it } }
        }
        return ViewPatched(id, name, published, entities?.map { it.first }, featured)
    }

    private fun associateEntitiesIdsToViewQuery(
        entitiesIds: List<entityIdWithAlias>,
        id: String
    ): List<ResultRow> = ViewEntities.batchInsert(entitiesIds, ignore = true) {
        this[ViewEntities.viewId] = id
        this[ViewEntities.entityId] = it.first
        this[ViewEntities.alias] = it.second
    }

    override suspend fun associateEntitiesIdsToView(
        entities: List<entityIdWithAlias>,
        id: String
    ) {
        newSuspendedTransaction(Dispatchers.IO, db) { associateEntitiesIdsToViewQuery(entities, id) }
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

    override suspend fun getViewEntity(viewId: String, entityId: Long): ViewEntity? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            ViewEntities.selectAll().where(ViewEntities.entityId.eq(entityId) and ViewEntities.viewId.eq(viewId))
                .map { resultRowToViewEntity(it) }.singleOrNull()
        }
    }

    override suspend fun state(): ViewsState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            ViewsState(
                Views.selectAll().map { resultRowToSimpleView(it) },
                ViewEntities.selectAll().map { resultRowToViewEntity(it) }
            )
        }
    }
}