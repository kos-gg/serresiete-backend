package com.kos.datacache

import com.kos.datacache.TestHelper.outdatedDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class DataCacheRepositoryTestCommon {

    abstract val repository: DataCacheRepository

    @Test
    open fun `given an empty repository i can insert data`() {
        runBlocking {
            assertEquals(listOf(), repository.state())
            assertEquals(true, repository.insert(listOf(wowDataCache)))
            assertEquals(listOf(wowDataCache), repository.state())
        }
    }

    @Test
    open fun `given a repository with a single cached data i can retrieve it`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(wowDataCache))
            assertEquals(listOf(wowDataCache), repositoryWithState.get(1))
        }
    }

    @Test
    open fun `given a repository with multiple cached data i can retrieve the only ones related to a certain character`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(wowDataCache, wowDataCache.copy(characterId = 2)))
            assertEquals(listOf(wowDataCache), repositoryWithState.get(1))
        }
    }

    @Test
    open fun `giver a repository with an expired record i can clear it`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(wowDataCache, outdatedDataCache))
            assertEquals(1, repositoryWithState.deleteExpiredRecord(24, null, true))
            assertEquals(listOf(wowDataCache), repositoryWithState.state())
        }
    }

    @Test
    open fun `given a repository with an expired record i can clear it unless it's the last record`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(outdatedDataCache))
            assertEquals(0, repositoryWithState.deleteExpiredRecord(24, null, false))
            assertEquals(listOf(outdatedDataCache), repositoryWithState.state())
        }
    }
}

class DataCacheInMemoryRepositoryTest : DataCacheRepositoryTestCommon() {
    override val repository = DataCacheInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataCacheDatabaseRepositoryTest : DataCacheRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = DataCacheDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
