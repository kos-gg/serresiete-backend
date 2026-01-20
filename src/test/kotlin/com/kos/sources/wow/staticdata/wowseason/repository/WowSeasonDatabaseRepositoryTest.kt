package com.kos.sources.wow.staticdata.wowseason.repository

import com.kos.assertTrue
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionDatabaseRepository
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionInMemoryRepository
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionRepository
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionState
import com.kos.sources.wow.staticdata.wowseason.WowSeason
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

abstract class WowSeasonDatabaseRepositoryTestCommon {
    abstract val repository: WowSeasonRepository
    abstract val wowExpansionRepository: WowExpansionRepository

    private val wowSeason = WowSeason(15, "TWW3", 10, "{}")

    @Test
    open fun `given an empty repository I can insert a wow season`() {
        runBlocking {
            wowExpansionRepository.withState(WowExpansionState(listOf(WowExpansion(10, "TWW", true))))
            val result = repository.insert(wowSeason)
            result.onLeft { fail() }
            result.onRight {
                assertTrue(it)
            }
            assertEquals(WowSeasonsState(listOf(wowSeason)), repository.state())
        }
    }
}

class WowSeasonInMemoryRepositoryTest : WowSeasonDatabaseRepositoryTestCommon() {
    override val repository = WowSeasonInMemoryRepository()
    override val wowExpansionRepository = WowExpansionInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowSeasonDatabaseRepositoryTest : WowSeasonDatabaseRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = WowSeasonDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
    override val wowExpansionRepository = WowExpansionDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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