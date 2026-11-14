package com.kos.entities.guilds.repository

import com.kos.entities.guilds.realmOrNull
import com.kos.entities.guilds.WowForeverGuild
import com.kos.entities.guilds.WowGuild
import com.kos.entities.guilds.Guild
import com.kos.common.getOrThrow
import com.kos.views.Game
import com.kos.views.repository.ViewsDatabaseRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowGuildsDatabaseRepository(private val db: Database) : WowGuildsRepository {

    object WowGuilds : Table("wow_guilds") {
        val blizzardId = long("blizzard_id")
        val name = text("name")
        val realm = text("realm").nullable()
        val region = text("region")
        val viewId = text("view_id").references(ViewsDatabaseRepository.Views.id, ReferenceOption.CASCADE)
        val game = text("game")

        override val primaryKey = PrimaryKey(blizzardId, game, viewId)
    }

    private fun rowToGuild(row: ResultRow): Pair<Guild, String> =
        (rowToWowGuild(row) ?: rowToForeverGuild(row)) to row[WowGuilds.viewId]

    private fun rowToWowGuild(row: ResultRow): WowGuild? =
        row[WowGuilds.realm]?.let { realm ->
            WowGuild(row[WowGuilds.name], realm, row[WowGuilds.region], row[WowGuilds.blizzardId])
        }

    private fun rowToForeverGuild(row: ResultRow): WowForeverGuild =
        WowForeverGuild(row[WowGuilds.name], row[WowGuilds.region], row[WowGuilds.blizzardId])

    override suspend fun insertGuild(guild: Guild, viewId: String, game: Game) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuilds.insertIgnore {
                it[WowGuilds.blizzardId] = guild.blizzardId
                it[WowGuilds.name] = guild.name.lowercase()
                it[WowGuilds.realm] = guild.realmOrNull()?.lowercase()
                it[WowGuilds.region] = guild.region.lowercase()
                it[WowGuilds.viewId] = viewId
                it[WowGuilds.game] = game.toString()
            }
        }
    }

    override suspend fun getWowGuilds(game: Game): List<Pair<WowGuild, String>> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuilds.selectAll().where { WowGuilds.game.eq(game.toString()) }
                .mapNotNull { row -> rowToWowGuild(row)?.let { it to row[WowGuilds.viewId] } }
        }

    override suspend fun getForeverGuilds(): List<Pair<WowForeverGuild, String>> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuilds.selectAll().where { WowGuilds.game.eq(Game.WOW_FOREVER.toString()) }
                .map { row -> rowToForeverGuild(row) to row[WowGuilds.viewId] }
        }

    override suspend fun findTrackedGuild(
        name: String,
        realm: String?,
        region: String,
        game: Game
    ): Pair<Guild, String>? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuilds.selectAll()
                .where {
                    WowGuilds.game.eq(game.toString())
                        .and(WowGuilds.name.eq(name.lowercase()))
                        .and(WowGuilds.realm.eq(realm?.lowercase()))
                        .and(WowGuilds.region.eq(region.lowercase()))
                }
                .limit(1)
                .map { rowToGuild(it) }
                .singleOrNull()
        }
    }

    override suspend fun state(): WowGuildsState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuildsState(
                WowGuilds.selectAll().map {
                    val (guild, viewId) = rowToGuild(it)
                    Triple(guild, viewId, Game.fromString(it[WowGuilds.game]).getOrThrow())
                }
            )
        }
    }

    override suspend fun withState(initialState: WowGuildsState): WowGuildsRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuilds.batchInsert(initialState.guilds) {
                this[WowGuilds.blizzardId] = it.first.blizzardId
                this[WowGuilds.name] = it.first.name.lowercase()
                this[WowGuilds.realm] = it.first.realmOrNull()?.lowercase()
                this[WowGuilds.region] = it.first.region.lowercase()
                this[WowGuilds.viewId] = it.second
                this[WowGuilds.game] = it.third.toString()
            }
        }

        return this
    }
}
