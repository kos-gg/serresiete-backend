package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.ControllerError

interface SyncProcessor {

    suspend fun sync(): Either<ControllerError, Unit>
}