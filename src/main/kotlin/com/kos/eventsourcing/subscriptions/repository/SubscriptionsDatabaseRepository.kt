package com.kos.eventsourcing.subscriptions.repository

import com.kos.eventsourcing.subscriptions.SubscriptionState
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class SubscriptionsDatabaseRepository(private val db: Database) : SubscriptionsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    object Subscriptions : Table("subscriptions") {
        val name = varchar("name", 128)
        val state = text("state")

        override val primaryKey = PrimaryKey(name)
    }

    private fun resultRowToSubscriptionState(row: ResultRow): SubscriptionState =
        json.decodeFromString(row[Subscriptions.state])

    private fun resultRowToSubscription(row: ResultRow): Pair<String, SubscriptionState> =
        Pair(row[Subscriptions.name], json.decodeFromString(row[Subscriptions.state]))

    override suspend fun getState(name: String): SubscriptionState? =
        newSuspendedTransaction(Dispatchers.IO, db) {
            Subscriptions.selectAll().where { Subscriptions.name.eq(name) }.map { resultRowToSubscriptionState(it) }.singleOrNull()
        }

    override suspend fun setState(subscriptionName: String, subscriptionState: SubscriptionState) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Subscriptions.update({ Subscriptions.name.eq(subscriptionName) }) {
                it[state] = json.encodeToString(subscriptionState)
            }
        }
    }

    override suspend fun getEventSubscriptions(): Map<String, SubscriptionState> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Subscriptions.selectAll().associate { resultRowToSubscription(it) }
        }
    }

    override suspend fun state(): Map<String, SubscriptionState> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Subscriptions.selectAll().associate { resultRowToSubscription(it) }
        }
    }

    override suspend fun withState(initialState: Map<String, SubscriptionState>): SubscriptionsRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Subscriptions.batchInsert(initialState.toList()) {
                this[Subscriptions.name] = it.first
                this[Subscriptions.state] = json.encodeToString(it.second)
            }
        }
        return this
    }
}