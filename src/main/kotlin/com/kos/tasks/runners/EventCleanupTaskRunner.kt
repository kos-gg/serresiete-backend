package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.common.fold
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType
import java.time.OffsetDateTime

class EventCleanupTaskRunner(
    private val eventStore: EventStore,
    private val subscriptionsRepository: SubscriptionsRepository
) : TaskRunner, WithLogger("eventCleanupTaskRunner") {

    override val type = TaskType.EVENT_CLEANUP_TASK
    private val olderThanDays: Long = 3

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Running event cleanup task")
        val cutoff = OffsetDateTime.now().minusDays(olderThanDays)
        val message = subscriptionsRepository.getEventSubscriptions().values.minOfOrNull { it.version }
            .fold(
                left = { "No subscriptions found, nothing deleted" },
                right = { minProcessedVersion ->
                    val deletedEvents = eventStore.deleteEventsOlderThan(cutoff, minProcessedVersion)
                    "Deleted $deletedEvents old events"
                }
            )
        logger.info(message)

        TaskCompleted()
    }
}
