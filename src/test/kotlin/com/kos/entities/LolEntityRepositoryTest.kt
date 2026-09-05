package com.kos.entities

import com.kos.common.WithState
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntity
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.repository.GameEntityRepository
import com.kos.entities.repository.LolEntityDatabaseRepository
import com.kos.entities.repository.LolEntityInMemoryRepository
import com.kos.views.Game
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class LolEntityRepositoryTestCommon<T> where T : GameEntityRepository, T : WithState<List<LolEntity>, T> {

    abstract val repository: T
    abstract val dataCacheRepository: DataCacheRepository

    @Test
    fun `given an empty repository i can insert lol characters`() {
        runBlocking {
            val expected = listOf(basicLolEntity)
            repository.insert(listOf(basicLolEntityEnrichedRequest)).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given a repository with lol characters, I can insert more`() {
        runBlocking {
            repository.withState(listOf(basicLolEntity))
            val request = basicLolEntityEnrichedRequest.copy(puuid = "different-puuid")
            val inserted = repository.insert(listOf(request))
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(2), characters.map { it.id }) }
                .onLeft { fail(it.message) }
        }
    }

    @Test
    fun `i can insert a lol character with a tag longer than 3 characters`() {
        runBlocking {
            val request = basicLolEntityEnrichedRequest.copy(tag = "12345")
            val inserted = repository.insert(listOf(request))
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(1), characters.map { it.id }) }
                .onLeft { fail(it.message) }
        }
    }

    @Test
    fun `inserting a request of the wrong type fails`() {
        runBlocking {
            assertTrue(repository.insert(listOf(basicWowRequest)).isLeft())
            assertEquals(listOf(), repository.state())
        }
    }

    @Test
    fun `given a repository with a lol character, i can update it`() {
        runBlocking {
            repository.withState(listOf(basicLolEntity))
            val updatedName = "Marcnute"
            val updatedTag = "EUW"
            val updatedSummonerIconId = 10
            val updatedSummonerLevel = 500
            val request = LolEnrichedEntityRequest(
                updatedName,
                updatedTag,
                basicLolEntity.puuid,
                updatedSummonerIconId,
                updatedSummonerLevel
            )
            val update = repository.update(1, request)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedTag, updated.tag)
            assertEquals(updatedSummonerIconId, updated.summonerIcon)
            assertEquals(updatedSummonerLevel, updated.summonerLevel)
            assertEquals(basicLolEntity.puuid, updated.puuid)
        }
    }

    @Test
    fun `updating a request of the wrong type fails`() {
        runBlocking {
            repository.withState(listOf(basicLolEntity))
            assertTrue(repository.update(1, basicWowRequest).isLeft())
        }
    }

    @Test
    fun `given a repository of lol characters, i can retrieve one by id`() {
        runBlocking {
            repository.withState(listOf(basicLolEntity))
            assertEquals(basicLolEntity, repository.get(basicLolEntity.id))
            assertEquals(null, repository.get(9999))
        }
    }

    @Test
    fun `given a repository of lol characters, i can retrieve one by a character request`() {
        runBlocking {
            repository.withState(listOf(basicLolEntity))
            val request: EntityRequest = LolEntityRequest(basicLolEntity.name, basicLolEntity.tag)
            assertEquals(basicLolEntity, repository.get(request))
        }
    }

    @Test
    fun `given a repository of lol characters, i can retrieve one by an insert request`() {
        runBlocking {
            repository.withState(listOf(basicLolEntity))
            val request: InsertEntityRequest = basicLolEntityEnrichedRequest
            assertEquals(basicLolEntity, repository.get(request))
        }
    }

    @Test
    fun `given a repository of lol characters, i can retrieve all of them`() {
        runBlocking {
            val lolEntity2 = basicLolEntity.copy(id = 2, puuid = "different-puuid")
            repository.withState(listOf(basicLolEntity, lolEntity2))
            assertEquals(listOf(basicLolEntity, lolEntity2), repository.getAll())
        }
    }

    @Test
    fun `get characters to sync should return those characters who don't have a cached record or were cached before olderThanMinutes`() {
        runBlocking {
            val lolEntities = (1..3).map {
                LolEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it, it)
            }
            repository.withState(lolEntities)

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now(), Game.LOL),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.LOL)
                )
            )
            val res = repository.getOlderThan(30, Int.MAX_VALUE)

            assertEquals(setOf<Long>(2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `get characters to sync should return all characters if all records were cached before olderThanMinutes`() {
        runBlocking {
            val lolEntities = (1..3).map {
                LolEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it, it)
            }
            repository.withState(lolEntities)

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(3, "", OffsetDateTime.now().minusMinutes(31), Game.LOL)
                )
            )
            val res = repository.getOlderThan(30, Int.MAX_VALUE)

            assertEquals(setOf<Long>(1, 2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `get characters to sync should return all characters if there's no cached records`() {
        runBlocking {
            val lolEntities = (1..3).map {
                LolEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it, it)
            }
            repository.withState(lolEntities)

            val res = repository.getOlderThan(30, Int.MAX_VALUE)

            assertEquals(setOf<Long>(1, 2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `get characters to sync should return no characters if they have been cached recently even if they have an old cached record`() {
        runBlocking {
            repository.withState(listOf(basicLolEntity))

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(1, "", OffsetDateTime.now(), Game.LOL)
                )
            )
            val res = repository.getOlderThan(30, Int.MAX_VALUE)

            assertEquals(listOf(), res.map { it.id })
        }
    }

    @Test
    fun `when there are more stale entities than the budget, only budget entities are returned`() {
        runBlocking {
            val lolEntities = (1..5).map {
                LolEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it, it)
            }
            repository.withState(lolEntities)

            val res = repository.getOlderThan(30, 2)

            assertEquals(2, res.size)
        }
    }

    @Test
    fun `when the budget is larger than the number of eligible entities, all eligible entities are returned`() {
        runBlocking {
            val lolEntities = (1..3).map {
                LolEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it, it)
            }
            repository.withState(lolEntities)

            val res = repository.getOlderThan(30, 10)

            assertEquals(setOf<Long>(1, 2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `entities that have never been synced are prioritized over stale ones when the budget is exceeded`() {
        runBlocking {
            val lolEntities = (1..4).map {
                LolEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it, it)
            }
            repository.withState(lolEntities)

            dataCacheRepository.withState(
                listOf(
                    DataCache(3, "", OffsetDateTime.now().minusMinutes(40), Game.LOL),
                    DataCache(4, "", OffsetDateTime.now().minusMinutes(35), Game.LOL)
                )
            )

            val res = repository.getOlderThan(30, 3)

            assertEquals(setOf<Long>(1, 2), res.map { it.id }.take(2).toSet())
            assertEquals(3L, res.map { it.id }[2])
        }
    }
}

class LolEntityInMemoryRepositoryTest : LolEntityRepositoryTestCommon<LolEntityInMemoryRepository>() {
    override val dataCacheRepository = DataCacheInMemoryRepository()
    override val repository: LolEntityInMemoryRepository by lazy {
        LolEntityInMemoryRepository(dataCacheRepository) {
            val ids = repository.state().map { it.id }
            if (ids.isEmpty()) 1L else ids.max() + 1
        }
    }

    @BeforeEach
    fun beforeEach() {
        dataCacheRepository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LolEntityDatabaseRepositoryTest : LolEntityRepositoryTestCommon<LolEntityDatabaseRepository>() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = LolEntityDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
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
