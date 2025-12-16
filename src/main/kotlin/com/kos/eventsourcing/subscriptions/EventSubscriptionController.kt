package com.kos.eventsourcing.subscriptions

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.ControllerError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions

class EventSubscriptionController(private val eventSubscriptionService: EventSubscriptionService) {
    suspend fun getEventSubscriptions(
        client: String?,
        activities: Set<Activity>
    ): Either<ControllerError, Map<String, SubscriptionState>> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.getEventSubscriptions)) Either.Right(eventSubscriptionService.getEventSubscriptions())
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }
}