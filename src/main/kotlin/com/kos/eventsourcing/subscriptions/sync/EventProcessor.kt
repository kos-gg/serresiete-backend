package com.kos.eventsourcing.subscriptions.sync

import com.kos.eventsourcing.subscriptions.EventProcessOutcome

sealed interface EventProcessor {

    suspend fun process(): EventProcessOutcome
}