package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.error.ServiceError

interface SyncProcessor {

    suspend fun sync(): Either<ServiceError, Unit>
}