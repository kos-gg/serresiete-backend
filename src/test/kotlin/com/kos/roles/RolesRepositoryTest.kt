package com.kos.roles

import com.kos.roles.repository.RolesDatabaseRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.roles.repository.RolesRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class RolesRepositoryTestCommon {

    abstract val repository: RolesRepository

    @Test
    fun `given a repository with roles i can retrieve them`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(Role.USER))
            assertEquals(repositoryWithState.getRoles(), listOf(Role.USER))
        }
    }
}

class RolesInMemoryRepositoryTest : RolesRepositoryTestCommon() {
    override val repository = RolesInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RolesDatabaseRepositoryTest : RolesRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = RolesDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
