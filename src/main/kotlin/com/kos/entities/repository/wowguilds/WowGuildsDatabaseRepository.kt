package com.kos.entities.repository.wowguilds

import arrow.core.Either
import com.kos.common.error.InsertError
import com.kos.entities.GuildPayload
import com.kos.views.repository.ViewsDatabaseRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.SQLException

class WowGuildsDatabaseRepository(private val db: Database) : WowGuildsRepository {

    object WowHardcoreGuilds : Table("wow_hardcore_guilds") {
        val blizzardId = long("blizzard_id")
        val name = text("name")
        val realm = text("realm")
        val region = text("region")
        val viewId = text("view_id").references(ViewsDatabaseRepository.Views.id, ReferenceOption.CASCADE)
    }

    private fun rowToGuildPayload(row: ResultRow): Pair<GuildPayload, String> {
        return GuildPayload(
            row[WowHardcoreGuilds.name],
            row[WowHardcoreGuilds.realm],
            row[WowHardcoreGuilds.region],
            row[WowHardcoreGuilds.blizzardId]
        ) to row[WowHardcoreGuilds.viewId]
    }

    override suspend fun insertGuild(
        blizzardId: Long,
        name: String,
        realm: String,
        region: String,
        viewId: String
    ): Either<InsertError, Unit> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            try {
                WowHardcoreGuilds.insert {
                    it[WowHardcoreGuilds.blizzardId] = blizzardId
                    it[WowHardcoreGuilds.name] = name
                    it[WowHardcoreGuilds.realm] = realm
                    it[WowHardcoreGuilds.region] = region
                    it[WowHardcoreGuilds.viewId] = viewId
                }
                Either.Right(Unit)
            } catch (e: SQLException) {
                if (e.sqlState == "23505") Either.Left(InsertError("Duplicated guild $name $realm $region"))
                else Either.Left(InsertError(e.message ?: e.stackTraceToString()))
            }
        }
    }

    override suspend fun getGuilds(): List<Pair<GuildPayload, String>> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowHardcoreGuilds.selectAll().map { rowToGuildPayload(it) }
        }
    }

    override suspend fun state(): WowGuildsState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowGuildsState(
                getGuilds()
            )
        }
    }

    override suspend fun withState(initialState: WowGuildsState): WowGuildsRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowHardcoreGuilds.batchInsert(initialState.guilds) {
                this[WowHardcoreGuilds.blizzardId] = it.first.blizzardId
                this[WowHardcoreGuilds.name] = it.first.name
                this[WowHardcoreGuilds.realm] = it.first.realm
                this[WowHardcoreGuilds.region] = it.first.region
                this[WowHardcoreGuilds.viewId] = it.second
            }
        }

        return this
    }
}