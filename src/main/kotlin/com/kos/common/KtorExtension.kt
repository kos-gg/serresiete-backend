package com.kos.common

import arrow.core.Either
import com.kos.credentials.Credentials
import com.kos.eventsourcing.subscriptions.EventSubscription
import io.ktor.server.application.*
import io.ktor.server.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration

fun UserPasswordCredential.toCredentials() = Credentials(this.name, this.password)

fun Application.launchSubscription(subscription: EventSubscription) {
    val backgroundJob = launch(Dispatchers.IO) {
        while (isActive) {
            Either.catch { subscription.processPendingEvents() }
                .onLeft { log.error("Error processing subscription: ${it.message}", it) }
            delay(Duration.ofSeconds(10).toMillis()) //TODO: We could want this to depend on subscription
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        backgroundJob.cancel()
    }
}