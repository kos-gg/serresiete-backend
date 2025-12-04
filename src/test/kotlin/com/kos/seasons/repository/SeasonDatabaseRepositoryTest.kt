package com.kos.seasons.repository

import com.kos.assertTrue
import com.kos.seasons.WowSeason
import com.kos.staticdata.WowExpansion
import com.kos.staticdata.repository.StaticDataDatabaseRepository
import com.kos.staticdata.repository.StaticDataInMemoryRepository
import com.kos.staticdata.repository.StaticDataRepository
import com.kos.staticdata.repository.StaticDataState
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

abstract class SeasonDatabaseRepositoryTestCommon {
    abstract val repository: SeasonRepository
    abstract val staticDataRepository: StaticDataRepository

    private val wowSeason = WowSeason(15, "TWW3", 10, "{}")

    @Test
    open fun `given an empty repository I can insert a wow season`() {
        runBlocking {
            staticDataRepository.withState(StaticDataState(listOf(WowExpansion(10, "TWW", true))))
            val result = repository.insert(wowSeason)
            result.onLeft { fail() }
            result.onRight {
                assertTrue(it)
            }
            assertEquals(SeasonsState(listOf(wowSeason)), repository.state())
        }
    }
}

class SeasonInMemoryRepositoryTest : SeasonDatabaseRepositoryTestCommon() {
    override val repository = SeasonInMemoryRepository()
    override val staticDataRepository = StaticDataInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeasonDatabaseRepositoryTest : SeasonDatabaseRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = SeasonDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
    override val staticDataRepository = StaticDataDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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