package com.kos.eventsourcing

import com.kos.eventsourcing.subscriptions.SubscriptionState
import com.kos.eventsourcing.subscriptions.SubscriptionStatus
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsDatabaseRepository
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsInMemoryRepository
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


abstract class SubscriptionRepositoryTest {
    abstract val repository: SubscriptionsRepository

    @Test
    fun `I can set the state of a subscription`() {
        runBlocking {
            val repoWithState = repository.withState(
                mapOf(
                    "testSubscription" to SubscriptionState(
                        SubscriptionStatus.WAITING,
                        0,
                        OffsetDateTime.now(),
                        null
                    )
                )
            )
            val newStateTime = OffsetDateTime.now()
            repoWithState.setState(
                "testSubscription", SubscriptionState(
                    SubscriptionStatus.RUNNING,
                    3,
                    newStateTime,
                    null
                )
            )

            val state = repoWithState.state()["testSubscription"]
            state?.let {
                assertEquals(SubscriptionStatus.RUNNING, it.status)
                assertEquals(3, it.version)
                assertEquals(newStateTime, it.time)
                assertEquals(null, it.lastError)
            }
        }
    }

    @Test
    fun `I can get the state of a subscription`() {
        runBlocking {
            val stateTime = OffsetDateTime.now()
            val repoWithState = repository.withState(
                mapOf(
                    "testSubscription" to SubscriptionState(
                        SubscriptionStatus.WAITING,
                        0,
                        stateTime,
                        null
                    )
                )
            )
            val state = repoWithState.getState("testSubscription")
            state?.let {
                assertEquals(SubscriptionStatus.WAITING, it.status)
                assertEquals(0, it.version)
                assertEquals(stateTime, it.time)
                assertEquals(null, it.lastError)
            }
        }
    }

    @Test
    fun `I can get the state of a subscriptions`() {
        runBlocking {
            val subscriptions = mapOf(
                "testSubscription" to SubscriptionState(
                    SubscriptionStatus.WAITING,
                    0,
                    OffsetDateTime.now(),
                    null
                )
            )

            val repoWithState = repository.withState(subscriptions)
            assertTrue { repoWithState.getEventSubscriptions().containsKey("testSubscription") }
        }
    }
}

class SubscriptionInMemoryRepositoryTest : SubscriptionRepositoryTest() {
    override val repository = SubscriptionsInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionDatabaseTest : SubscriptionRepositoryTest() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = SubscriptionsDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

    @BeforeEach
    fun beforeEach() {
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun afterAll() {
        embeddedPostgres.close()
    }
}