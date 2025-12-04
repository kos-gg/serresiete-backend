package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.ControllerError
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.views.ViewsService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ViewsSyncProcessor(
    eventWithVersion: EventWithVersion,
    viewsService: ViewsService
) : SyncProcessor {
    override val logger: Logger = LoggerFactory.getLogger("eventSubscription.viewsProcessor")
    override suspend fun sync(): Either<ControllerError, Unit> {
        TODO("Not yet implemented")
    }
}