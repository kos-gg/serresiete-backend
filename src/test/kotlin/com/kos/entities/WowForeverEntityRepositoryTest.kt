package com.kos.entities

import com.kos.common.WithState
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowForeverEntity
import com.kos.entities.EntitiesTestHelper.basicWowForeverRequest
import com.kos.entities.EntitiesTestHelper.basicWowForeverRequestWithBlizzardId
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.repository.GameEntityRepository
import com.kos.entities.repository.WowForeverEntityDatabaseRepository
import com.kos.entities.repository.WowForeverEntityInMemoryRepository
import com.kos.views.Game
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class WowForeverEntityRepositoryTestCommon<T> where T : GameEntityRepository, T : WithState<List<WowForeverEntity>, T> {

    abstract val repository: T
    abstract val dataCacheRepository: DataCacheRepository

    @Test
    fun `given an empty repository i can insert wow forever characters`() {
        runBlocking {
            assertEquals(listOf(basicWowForeverEntity), repository.insert(listOf(basicWowForeverRequestWithBlizzardId)))
        }
    }

    @Test
    fun `name and surname are stored lowercased`() {
        runBlocking {
            repository.insert(listOf(basicWowForeverRequestWithBlizzardId.copy(name = "Sanxei", surname = "Tarde")))
            assertEquals(listOf(basicWowForeverEntity), repository.state())
        }
    }

    @Test
    fun `inserting a wow forever character with the same blizzardId as an existing one fails`() {
        runBlocking {
            repository.withState(listOf(basicWowForeverEntity))
            assertThrows<Exception> {
                repository.insert(listOf(basicWowForeverRequestWithBlizzardId.copy(name = "other")))
            }
        }
    }

    @Test
    fun `inserting a request of the wrong type fails`() {
        runBlocking {
            assertThrows<Exception> { repository.insert(listOf(basicLolEntityEnrichedRequest)) }
        }
    }

    @Test
    fun `given a repository with a wow forever character, i can update it`() {
        runBlocking {
            repository.withState(listOf(basicWowForeverEntity))
            assertEquals(1, repository.update(basicWowForeverEntity.id, basicWowForeverRequest.copy(surname = "pronto")))
            assertEquals(listOf(basicWowForeverEntity.copy(surname = "pronto")), repository.state())
        }
    }

    @Test
    fun `i can retrieve a wow forever character by id`() {
        runBlocking {
            repository.withState(listOf(basicWowForeverEntity))
            assertEquals(basicWowForeverEntity, repository.get(basicWowForeverEntity.id))
        }
    }

    @Test
    fun `i can retrieve a wow forever character by name, surname and region ignoring case`() {
        runBlocking {
            repository.withState(listOf(basicWowForeverEntity))
            val request: EntityRequest = basicWowForeverRequest.copy(name = "Sanxei", surname = "TARDE")
            assertEquals(basicWowForeverEntity, repository.get(request))
        }
    }

    @Test
    fun `a character with the same name but different surname is not found`() {
        runBlocking {
            repository.withState(listOf(basicWowForeverEntity))
            val request: EntityRequest = basicWowForeverRequest.copy(surname = "pronto")
            assertEquals(null, repository.get(request))
        }
    }

    @Test
    fun `i can retrieve a wow forever character by an insert request with blizzardId`() {
        runBlocking {
            repository.withState(listOf(basicWowForeverEntity))
            val request: InsertEntityRequest = basicWowForeverRequestWithBlizzardId.copy(name = "renamed")
            assertEquals(basicWowForeverEntity, repository.get(request))
        }
    }

    @Test
    fun `get characters to sync returns only wow forever characters with stale or missing cache`() {
        runBlocking {
            val fresh = basicWowForeverEntity
            val stale = basicWowForeverEntity.copy(id = 2, name = "stale", blizzardId = 2)
            val neverCached = basicWowForeverEntity.copy(id = 3, name = "never", blizzardId = 3)
            repository.withState(listOf(fresh, stale, neverCached))
            dataCacheRepository.insert(
                listOf(
                    DataCache(fresh.id, "", OffsetDateTime.now(), Game.WOW_FOREVER),
                    DataCache(stale.id, "", OffsetDateTime.now().minusHours(2), Game.WOW_FOREVER)
                )
            )

            assertEquals(listOf(neverCached, stale), repository.getOlderThan(30, 10))
        }
    }
}

class WowForeverEntityInMemoryRepositoryTest :
    WowForeverEntityRepositoryTestCommon<WowForeverEntityInMemoryRepository>() {
    override val dataCacheRepository = DataCacheInMemoryRepository()
    override val repository: WowForeverEntityInMemoryRepository by lazy {
        WowForeverEntityInMemoryRepository(dataCacheRepository) {
            val ids = repository.state().map { it.id }
            if (ids.isEmpty()) 1L else ids.max() + 1
        }
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowForeverEntityDatabaseRepositoryTest :
    WowForeverEntityRepositoryTestCommon<WowForeverEntityDatabaseRepository>() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = WowForeverEntityDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
    override val dataCacheRepository = DataCacheDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
