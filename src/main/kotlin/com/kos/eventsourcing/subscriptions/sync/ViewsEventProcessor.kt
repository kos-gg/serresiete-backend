package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.eventsourcing.events.*
import com.kos.views.ViewsService

class ViewsEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val viewsService: ViewsService
) : EventProcessor, WithLogger("eventSubscription.viewsProcessor") {

    override suspend fun process(): Either<ServiceError, Unit> {
        val operationId = eventWithVersion.event.operationId
        val aggregateRoot = eventWithVersion.event.aggregateRoot
        logger.debug("processing event v${eventWithVersion.version}")

        val eventType = eventWithVersion.event.eventData.eventType
        when (eventType) {
            EventType.VIEW_TO_BE_CREATED ->
                viewsService.createView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeCreatedEvent
                ).onLeft { recordFailure(operationId, aggregateRoot, it.error()) }

            EventType.VIEW_TO_BE_EDITED ->
                viewsService.editView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeEditedEvent
                ).onLeft { recordFailure(operationId, aggregateRoot, it.error()) }

            EventType.VIEW_TO_BE_PATCHED ->
                viewsService.patchView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBePatchedEvent
                ).onLeft { recordFailure(operationId, aggregateRoot, it.error()) }

            EventType.VIEW_TO_BE_DELETED ->
                viewsService.deleteView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeDeletedEvent
                ).onLeft { recordFailure(operationId, aggregateRoot, it.error()) }

            else -> logger.debug("skipping event v${eventWithVersion.version} (${eventWithVersion.event.eventData.eventType})")
        }

        return Either.Right(Unit)
    }

    private suspend fun recordFailure(operationId: String, aggregateRoot: String, reason: String) {
        logger.error("operation $operationId failed: $reason")
        runCatching { viewsService.failOperation(operationId, aggregateRoot, reason) }
            .onFailure { e -> logger.error("failed to store OperationFailedEvent: ${e.message}") }
    }
}