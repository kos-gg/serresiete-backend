package com.kos.sources.wow.staticdata.wowexpansion.repository

import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowExpansionDatabaseRepository(private val db: Database) : WowExpansionRepository {
    object WowExpansions : Table("wow_expansions") {
        val id = integer("id")
        val name = text("name")
        val isCurrentExpansion = bool("is_current_expansion")

        override val primaryKey = PrimaryKey(id, name)
    }

    override suspend fun getExpansions(): List<WowExpansion> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowExpansions.selectAll().map {
                resultRowToWowExpansion(it)
            }
        }
    }

    private fun resultRowToWowExpansion(it: ResultRow) = WowExpansion(
        it[WowExpansions.id],
        it[WowExpansions.name],
        it[WowExpansions.isCurrentExpansion],
    )

    override suspend fun state(): WowExpansionState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowExpansionState(
                WowExpansions.selectAll().map { resultRowToWowExpansion(it) }
            )
        }
    }

    override suspend fun withState(initialState: WowExpansionState): WowExpansionRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowExpansions.batchInsert(initialState.wowExpansions) {
                this[WowExpansions.id] = it.id
                this[WowExpansions.name] = it.name
                this[WowExpansions.isCurrentExpansion] = it.isCurrentExpansion
            }
        }
        return this
    }
}