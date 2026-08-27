package com.kos.entities.repository.wowguilds

import arrow.core.Either
import com.kos.common.error.InsertError
import com.kos.common.getOrThrow
import com.kos.entities.domain.GuildPayload
import com.kos.views.Game
import com.kos.views.repository.ViewsDatabaseRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.SQLException

class WowGuildsDatabaseRepository(private val db: Database) : WowGuildsRepository {

    object WowGuilds : Table("wow_guilds") {
        val blizzardId = long("blizzard_id")
        val name = text("name")
        val realm = text("realm")
        val region = text("region")
        val viewId = text("view_id").references(ViewsDatabaseRepository.Views.id, ReferenceOption.CASCADE)
        val game = text("game")

        override val primaryKey = PrimaryKey(blizzardId, game)
    }

    private fun rowToGuildPayload(row: ResultRow): Pair<GuildPayload, String> {
        return GuildPayload(
            row[WowGuilds.name],
            row[WowGuilds.realm],
            row[WowGuilds.region],
            row[WowGuilds.blizzardId]
        ) to row[WowGuilds.viewId]
    }

    override suspend fun insertGuild(
        blizzardId: Long,
        name: String,
        realm: String,
        region: String,
        viewId: String,
        game: Game
    ): Either<InsertError, Unit> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val existingViewId = WowGuilds.selectAll()
                .where { WowGuilds.blizzardId.eq(blizzardId).and(WowGuilds.game.eq(game.toString())) }
                .singleOrNull()?.get(WowGuilds.viewId)
            when {
                existingViewId == viewId -> Either.Right(Unit)
                existingViewId != null -> Either.Left(InsertError("Duplicated guild $name $realm $region"))
                else -> try {
                    WowGuilds.insert {
                        it[WowGuilds.blizzardId] = blizzardId
                        it[WowGuilds.name] = name
                        it[WowGuilds.realm] = realm
                        it[WowGuilds.region] = region
                        it[WowGuilds.viewId] = viewId
                        it[WowGuilds.game] = game.toString()
                    }
                    Either.Right(Unit)
                } catch (e: SQLException) {
                    if (e.sqlState == "23505") Either.Left(InsertError("Duplicated guild $name $realm $region"))
                    else Either.Left(InsertError(e.message ?: e.stackTraceToString()))
                }
            }
        }
    }

    override suspend fun getGuilds(game: Game): List<Pair<GuildPayload, String>> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuilds.selectAll().where { WowGuilds.game.eq(game.toString()) }.map { rowToGuildPayload(it) }
        }
    }

    override suspend fun state(): WowGuildsState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuildsState(
                WowGuilds.selectAll().map {
                    val (guild, viewId) = rowToGuildPayload(it)
                    Triple(guild, viewId, Game.fromString(it[WowGuilds.game]).getOrThrow())
                }
            )
        }
    }

    override suspend fun withState(initialState: WowGuildsState): WowGuildsRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuilds.batchInsert(initialState.guilds) {
                this[WowGuilds.blizzardId] = it.first.blizzardId
                this[WowGuilds.name] = it.first.name
                this[WowGuilds.realm] = it.first.realm
                this[WowGuilds.region] = it.first.region
                this[WowGuilds.viewId] = it.second
                this[WowGuilds.game] = it.third.toString()
            }
        }

        return this
    }
}
