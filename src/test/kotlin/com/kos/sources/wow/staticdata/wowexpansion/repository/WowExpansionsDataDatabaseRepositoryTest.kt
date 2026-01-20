package com.kos.sources.wow.staticdata.wowexpansion.repository

import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

abstract class WowExpansionsRepositoryTestCommon {
    abstract val repository: WowExpansionRepository

    @Test
    fun `I can retrieve wow expansions`() {
        runBlocking {
            val staticDataRepository =
                repository.withState(WowExpansionState(listOf(WowExpansion(10, "TWW", true))))

            assertEquals(staticDataRepository.getExpansions()[0].id, 10)
        }

    }
}

class WowExpansionsInMemoryRepositoryTest : WowExpansionsRepositoryTestCommon() {
    override val repository = WowExpansionInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowExpansionsDatabaseRepositoryTest : WowExpansionsRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()


    override val repository = WowExpansionDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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