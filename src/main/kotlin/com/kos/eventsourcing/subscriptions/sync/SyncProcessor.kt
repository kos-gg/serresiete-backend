package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.ControllerError
import org.slf4j.Logger

interface SyncProcessor {

    val logger: Logger
    suspend fun sync(): Either<ControllerError, Unit>
}