package com.kos.operations

import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OperationsServiceTest {

    private val eventStore = EventStoreInMemory()
    private val service = OperationsService(eventStore)

    private val operationId = UUID.randomUUID().toString()
    private val aggregateRoot = "/credentials/sanxei"

    @BeforeTest
    fun beforeEach() {
        eventStore.clear()
    }

    @Test
    fun `returns null when no events exist for the operation`() {
        runBlocking {
            assertNull(service.getOperationStatus("unknown-op"))
        }
    }

    @Test
    fun `returns pending when events exist but none are completion or failure events`() {
        runBlocking {
            val event = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(), "view-name", false, listOf(), Game.WOW, "sanxei", false, null
            )
            eventStore.save(Event(aggregateRoot, operationId, event))

            assertEquals(
                OperationStatus(operationId, OperationStatusType.PENDING),
                service.getOperationStatus(operationId)
            )
        }
    }

    @Test
    fun `returns completed when a view sync completed event exists`() {
        runBlocking {
            val viewId = UUID.randomUUID().toString()
            eventStore.save(Event(aggregateRoot, operationId, ViewToBeCreatedEvent(
                viewId, "view-name", false, listOf(), Game.WOW, "sanxei", false, null
            )))
            eventStore.save(Event(aggregateRoot, operationId, ViewSyncCompletedEvent(viewId)))

            assertEquals(
                OperationStatus(operationId, OperationStatusType.COMPLETED),
                service.getOperationStatus(operationId)
            )
        }
    }

    @Test
    fun `returns completed when a view deleted event exists`() {
        runBlocking {
            val viewId = UUID.randomUUID().toString()
            eventStore.save(Event(aggregateRoot, operationId, ViewDeletedEvent(
                viewId, "view-name", "sanxei", listOf(), false, Game.WOW, false
            )))

            assertEquals(
                OperationStatus(operationId, OperationStatusType.COMPLETED),
                service.getOperationStatus(operationId)
            )
        }
    }

    @Test
    fun `returns failed with reason when an operation failed event exists`() {
        runBlocking {
            val reason = "external service unavailable"
            eventStore.save(Event(aggregateRoot, operationId, OperationFailedEvent(operationId, reason)))

            assertEquals(
                OperationStatus(operationId, OperationStatusType.FAILED, reason = reason),
                service.getOperationStatus(operationId)
            )
        }
    }

    @Test
    fun `failed takes precedence over completion when both events exist`() {
        runBlocking {
            val viewId = UUID.randomUUID().toString()
            val reason = "something went wrong"
            eventStore.save(Event(aggregateRoot, operationId, ViewSyncCompletedEvent(viewId)))
            eventStore.save(Event(aggregateRoot, operationId, OperationFailedEvent(operationId, reason)))

            assertEquals(
                OperationStatus(operationId, OperationStatusType.FAILED, reason = reason),
                service.getOperationStatus(operationId)
            )
        }
    }
}
