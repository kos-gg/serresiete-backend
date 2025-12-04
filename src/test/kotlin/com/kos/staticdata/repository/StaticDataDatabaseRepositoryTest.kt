package com.kos.staticdata.repository

import com.kos.staticdata.WowExpansion
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

abstract class StaticDataRepositoryTestCommon {
    abstract val repository: StaticDataRepository

    @Test
    fun `I can retrieve wow expansions`() {
        runBlocking {
            val staticDataRepository =
                repository.withState(StaticDataState(listOf(WowExpansion(10, "TWW", true))))

            assertEquals(staticDataRepository.getExpansions()[0].id, 10)
        }

    }
}

class StaticInMemoryRepositoryTest : StaticDataRepositoryTestCommon() {
    override val repository = StaticDataInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaticDataDatabaseRepositoryTest : StaticDataRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()


    override val repository = StaticDataDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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