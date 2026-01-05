package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.ControllerError

interface EventProcessor {

    suspend fun process(): Either<ControllerError, Unit>
}