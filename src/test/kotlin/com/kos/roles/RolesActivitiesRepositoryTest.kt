package com.kos.roles

import com.kos.activities.ActivitiesTestHelper.basicActivity
import com.kos.roles.RolesTestHelper.basicRolesActivities
import com.kos.roles.repository.RolesActivitiesDatabaseRepository
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesActivitiesRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class RolesActivitiesRepositoryTest {

    abstract val repository: RolesActivitiesRepository

    @Test
    fun `given a repository with roles and activities i can retrieve activities from a given role`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicRolesActivities)
            assertTrue { repositoryWithState.getActivitiesFromRole(Role.USER).contains(basicActivity) }
        }
    }

    @Test
    fun `given an empty repository i can set activities of a role`() {
        runBlocking {
            repository.updateActivitiesFromRole(Role.USER, setOf(basicActivity))
            assertEquals(setOf(basicActivity), repository.state()[Role.USER])
        }
    }

    @Test
    fun `given a repository with a role with activities i can set activities of a role`() {
        runBlocking {
            repository.withState(mapOf(Role.USER to setOf("activity")))
            repository.updateActivitiesFromRole(Role.USER, setOf(basicActivity))
            assertEquals(setOf(basicActivity), repository.state()[Role.USER])
        }
    }

}

class RolesActivitiesInMemoryRepositoryTest : RolesActivitiesRepositoryTest() {
    override val repository = RolesActivitiesInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RolesActivitiesDatabaseRepositoryTest : RolesActivitiesRepositoryTest() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = RolesActivitiesDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
