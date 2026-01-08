package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.error.ServiceError

interface EventProcessor {

    suspend fun process(): Either<ServiceError, Unit>
}