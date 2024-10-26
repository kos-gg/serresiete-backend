package com.kos.activities

import com.kos.activities.ActivitiesTestHelper.basicActivities
import com.kos.activities.ActivitiesTestHelper.basicActivity
import com.kos.activities.repository.ActivitiesDatabaseRepository
import com.kos.activities.repository.ActivitiesInMemoryRepository
import com.kos.activities.repository.ActivitiesRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.*

abstract class ActivitiesRepositoryTestCommon {

    abstract val repository: ActivitiesRepository

    @Test
    fun `given a repository with activities i can retrieve them`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicActivities)
            assertEquals(repositoryWithState.getActivities(), basicActivities)
        }
    }

    @Test
    fun `given an empty repository i can insert an activity`() {
        runBlocking {
            repository.insertActivity(basicActivity)
            val state = repository.state()
            assertContains(state, basicActivity)
        }
    }

    @Test
    fun `given a repository with one activity i can delete it`() {
        runBlocking {
            val repositoryWithState = repository.withState(setOf(basicActivity))
            repositoryWithState.deleteActivity(basicActivity)
            assertTrue(repositoryWithState.state().isEmpty())
        }
    }
}

class ActivitiesInMemoryRepositoryTest : ActivitiesRepositoryTestCommon() {
    override val repository = ActivitiesInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActivitiesDatabaseRepositoryTest : ActivitiesRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository: ActivitiesRepository =
        ActivitiesDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
