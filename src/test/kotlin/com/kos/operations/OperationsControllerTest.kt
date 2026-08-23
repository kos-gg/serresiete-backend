package com.kos.operations

import com.kos.activities.Activities
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.common.error.NotFound
import com.kos.common.getLeftOrNull
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.OperationFailedEvent
import com.kos.eventsourcing.events.ViewSyncCompletedEvent
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OperationsControllerTest {

    private val eventStore = EventStoreInMemory()
    private val controller = OperationsController(OperationsService(eventStore))

    private val operationId = UUID.randomUUID().toString()
    private val aggregateRoot = "/credentials/sanxei"

    @BeforeTest
    fun beforeEach() {
        eventStore.clear()
    }

    @Test
    fun `returns not authorized when client is null`() {
        runBlocking {
            assertEquals(
                NotAuthorized,
                controller.getOperationStatus(null, operationId, setOf(Activities.getOperationStatus)).getLeftOrNull()
            )
        }
    }

    @Test
    fun `returns not enough permissions when activity is missing`() {
        runBlocking {
            assertEquals(
                NotEnoughPermissions("sanxei"),
                controller.getOperationStatus("sanxei", operationId, setOf()).getLeftOrNull()
            )
        }
    }

    @Test
    fun `returns not found when no events exist for the operation`() {
        runBlocking {
            assertEquals(
                NotFound("unknown-op"),
                controller.getOperationStatus("sanxei", "unknown-op", setOf(Activities.getOperationStatus))
                    .getLeftOrNull()
            )
        }
    }

    @Test
    fun `returns pending status for an in-progress operation`() {
        runBlocking {
            eventStore.save(
                Event(
                    aggregateRoot, operationId, ViewToBeCreatedEvent(
                        UUID.randomUUID().toString(), "view-name", false, listOf(), Game.WOW, "sanxei", false, null
                    )
                )
            )

            assertEquals(
                OperationStatus(operationId, OperationStatusType.PENDING),
                controller.getOperationStatus("sanxei", operationId, setOf(Activities.getOperationStatus)).getOrNull()
            )
        }
    }

    @Test
    fun `returns completed status when sync is done`() {
        runBlocking {
            val viewId = UUID.randomUUID().toString()
            eventStore.save(Event(aggregateRoot, operationId, ViewSyncCompletedEvent(viewId)))

            assertEquals(
                OperationStatus(operationId, OperationStatusType.COMPLETED),
                controller.getOperationStatus("sanxei", operationId, setOf(Activities.getOperationStatus)).getOrNull()
            )
        }
    }

    @Test
    fun `returns failed status with reason when operation failed`() {
        runBlocking {
            val reason = "external service unavailable"
            eventStore.save(Event(aggregateRoot, operationId, OperationFailedEvent(operationId, reason)))

            assertEquals(
                OperationStatus(operationId, OperationStatusType.FAILED, reason = reason),
                controller.getOperationStatus("sanxei", operationId, setOf(Activities.getOperationStatus)).getOrNull()
            )
        }
    }
}
