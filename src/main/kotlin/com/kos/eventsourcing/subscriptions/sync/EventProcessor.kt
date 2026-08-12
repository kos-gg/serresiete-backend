package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.error.ServiceError
import com.kos.eventsourcing.subscriptions.EventProcessOutcome

sealed interface EventProcessor {

    suspend fun process(): Either<ServiceError, EventProcessOutcome>
}