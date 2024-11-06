package com.kos.eventsourcing.events.repository

import com.kos.common._fold
import com.kos.eventsourcing.events.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.sequences.Sequence

class EventStoreDatabase(private val db: Database) : EventStore {

    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(EventData::class) {
                subclass(ViewToBeCreatedEvent::class, ViewToBeCreatedEvent.serializer())
                subclass(ViewToBePatchedEvent::class, ViewToBePatchedEvent.serializer())
                subclass(ViewToBeEditedEvent::class, ViewToBeEditedEvent.serializer())
                subclass(ViewCreatedEvent::class, ViewCreatedEvent.serializer())
                subclass(ViewEditedEvent::class, ViewEditedEvent.serializer())
                subclass(ViewPatchedEvent::class, ViewPatchedEvent.serializer())
            }
        }
        ignoreUnknownKeys = true
    }

    object Events : Table() {
        val version = long("version")
        val aggregateRoot = varchar("aggregate_root", 128)
        val operationId = varchar("operation_id", 128)
        val eventType = varchar("event_type", 48)
        val data = text("data")

        override val primaryKey = PrimaryKey(version)
        override val tableName = "events"
    }

    private fun resultRowToEventWithVersion(row: ResultRow): EventWithVersion =
        EventWithVersion(
            row[Events.version],
            Event(row[Events.aggregateRoot], row[Events.operationId], json.decodeFromString(row[Events.data]))
        )

    private fun resultRowToOperation(row: ResultRow): Operation =
        Operation(
            row[Events.operationId],
            row[Events.aggregateRoot],
            EventType.fromString(row[Events.eventType])
        )


    override suspend fun save(event: Event): Operation {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val id = selectNextId()
            Events.insert {
                it[aggregateRoot] = event.aggregateRoot
                it[operationId] = event.operationId
                it[version] = id
                it[eventType] = event.eventData.eventType.toString()
                it[data] = json.encodeToString(event.eventData)
            }.resultedValues?.map { resultRowToOperation(it) }?.singleOrNull() ?: throw Exception(":(")
        }
    }

    override suspend fun getEvents(version: Long?): Sequence<EventWithVersion> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            version._fold(
                left = { Events.selectAll().map { resultRowToEventWithVersion(it) }.asSequence() },
                right = {
                    Events.select { Events.version greater it }.orderBy(Events.version)
                        .map { resultRowToEventWithVersion(it) }.asSequence()
                })
        }
    }

    override suspend fun state(): List<EventWithVersion> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Events.selectAll().map { resultRowToEventWithVersion(it) }
        }
    }

    override suspend fun withState(initialState: List<EventWithVersion>): EventStore {
        TODO("Not yet implemented")
    }

    private suspend fun selectNextId(): Long =
        newSuspendedTransaction(Dispatchers.IO, db) {
            TransactionManager.current().exec("""select nextval('event_versions') as id""") { rs ->
                if (rs.next()) rs.getLong("id")
                else -1
            }
        } ?: -1
}