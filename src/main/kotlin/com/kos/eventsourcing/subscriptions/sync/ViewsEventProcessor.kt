package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.eventsourcing.events.*
import com.kos.views.ViewsService

class ViewsEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val viewsService: ViewsService
) : EventProcessor, WithLogger("eventSubscription.viewsProcessor") {

    override suspend fun process(): Either<ServiceError, Unit> {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_TO_BE_CREATED -> {
                either {
                    logger.debug("processing event v${eventWithVersion.version}")
                    val payload = eventWithVersion.event.eventData as ViewToBeCreatedEvent
                    val aggregateRoot = eventWithVersion.event.aggregateRoot
                    val operationId = eventWithVersion.event.operationId
                    viewsService.createView(operationId, aggregateRoot, payload).bind()
                }
            }

            EventType.VIEW_TO_BE_EDITED -> {
                either {
                    logger.debug("processing event v${eventWithVersion.version}")
                    val payload = eventWithVersion.event.eventData as ViewToBeEditedEvent
                    val aggregateRoot = eventWithVersion.event.aggregateRoot
                    val operationId = eventWithVersion.event.operationId
                    viewsService.editView(operationId, aggregateRoot, payload).bind()
                }
            }

            EventType.VIEW_TO_BE_PATCHED -> {
                either {
                    logger.debug("processing event v${eventWithVersion.version}")
                    val payload = eventWithVersion.event.eventData as ViewToBePatchedEvent
                    val aggregateRoot = eventWithVersion.event.aggregateRoot
                    val operationId = eventWithVersion.event.operationId
                    viewsService.patchView(operationId, aggregateRoot, payload).bind()
                }
            }

            else -> {
                logger.debug(
                    "skipping event v{} ({})",
                    eventWithVersion.version,
                    eventWithVersion.event.eventData.eventType
                )
                Either.Right(Unit)
            }
        }
    }
}