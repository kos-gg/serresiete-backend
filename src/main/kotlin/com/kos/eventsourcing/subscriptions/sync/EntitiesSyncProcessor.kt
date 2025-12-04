package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.ControllerError
import com.kos.entities.EntitiesService
import com.kos.eventsourcing.events.EventWithVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EntitiesSyncProcessor(
    eventWithVersion: EventWithVersion,
    entitiesService: EntitiesService,
) : SyncProcessor {
    override val logger: Logger = LoggerFactory.getLogger("eventSubscription.entitiesProcessor")

    override suspend fun sync(): Either<ControllerError, Unit> {
        TODO("Not yet implemented")
    }

}