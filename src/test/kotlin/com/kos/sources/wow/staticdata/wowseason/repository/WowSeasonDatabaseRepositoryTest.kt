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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

abstract class WowSeasonDatabaseRepositoryTestCommon {
    abstract val repository: WowSeasonRepository
    abstract val wowExpansionRepository: WowExpansionRepository

    private val wowSeason = WowSeason(15, "TWW3", 10, "{}", true)

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

    @Test
    open fun `given a repository with a season I can insert a wow season`() {
        runBlocking {
            wowExpansionRepository.withState(WowExpansionState(listOf(WowExpansion(10, "TWW", true))))
            repository.withState(WowSeasonsState(listOf(wowSeason)))
            val newSeason = wowSeason.copy(id = 16)
            val result = repository.insert(newSeason)
            result.onLeft { fail() }
            result.onRight { assertTrue(it) }
            assertEquals(
                WowSeasonsState(listOf(wowSeason.copy(isCurrentSeason = false), newSeason)),
                repository.state()
            )
        }
    }

    @Test
    open fun `given a repository with a season if I insert a wow season with no current, then the others don't get updated`() {
        runBlocking {
            wowExpansionRepository.withState(WowExpansionState(listOf(WowExpansion(10, "TWW", true))))
            repository.withState(WowSeasonsState(listOf(wowSeason)))
            val newSeason = wowSeason.copy(id = 16, isCurrentSeason = false)
            val result = repository.insert(newSeason)
            result.onLeft { fail() }
            result.onRight { assertTrue(it) }
            assertEquals(
                WowSeasonsState(listOf(wowSeason, newSeason)),
                repository.state()
            )
        }
    }

    @Test
    open fun `given a repository with a current season i can retrieve it`() {
        runBlocking {
            wowExpansionRepository.withState(WowExpansionState(listOf(WowExpansion(10, "TWW", true))))
            repository.withState(WowSeasonsState(listOf(wowSeason)))
            val result = repository.getCurrentSeason()
            assertEquals(
                wowSeason,
                result
            )
        }
    }

    @Test
    open fun `given an empty repository, after 1000 inserts, only 1 current season remains`() {
        runBlocking {
            wowExpansionRepository.withState(WowExpansionState(listOf(WowExpansion(10, "TWW", true))))
            repeat(1000) { index ->
                repository.insert(
                    WowSeason(
                        id = index,
                        name = "Season $index",
                        expansionId = 10,
                        seasonData = "data-$index",
                        isCurrentSeason = Random.nextInt() % 2 == 0
                    )
                )
            }
            assertEquals(1, repository.state().wowSeasons.filter { it.isCurrentSeason }.size)
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
    override val wowExpansionRepository =
        WowExpansionDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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