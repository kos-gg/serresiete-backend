package com.kos.entities

import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity2
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.EntitiesState
import com.kos.views.Game
import com.kos.views.ViewEntity
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.repository.ViewsDatabaseRepository
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsRepository
import com.kos.views.repository.ViewsState
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
import kotlin.test.fail

/**
 * Per-game persistence behavior (type validation, uniqueness, staleness budgeting, etc.) is covered by
 * WowEntityRepositoryTest / WowHardcoreEntityRepositoryTest / LolEntityRepositoryTest. This suite only exercises
 * that EntitiesRepository correctly dispatches each interface method to the right game, plus the cross-game
 * concerns (delete, state/withState, getViewsFromEntity) that live outside any single GameEntityRepository.
 */
abstract class EntitiesRepositoryTestCommon {

    abstract val repository: EntitiesRepository
    abstract val dataCacheRepository: DataCacheRepository
    abstract val viewsRepository: ViewsRepository

    @Test
    fun `given an empty repository i can insert wow characters`() {
        runBlocking {
            val expected = listOf(basicWowEntity)
            repository.insert(listOf(basicWowRequest), Game.WOW).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given an empty repository, I can't insert characters when game does not match`() {
        runBlocking {
            assertTrue(repository.insert(listOf(basicLolEntityEnrichedRequest), Game.WOW).isLeft())
            assertTrue(repository.insert(listOf(basicWowRequest), Game.LOL).isLeft())
            assertEquals(emptyEntitiesState, repository.state())
        }
    }

    @Test
    fun `given a repository with a wow character, i can update it`() {
        runBlocking {
            repository.withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val updatedName = "camilo"
            val updatedRegion = "eu"
            val updatedRealm = "stitches"
            val request = WowEntityRequest(updatedName, updatedRegion, updatedRealm)
            val update = repository.update(1, request, Game.WOW)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().wowEntities.first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedRegion, updated.region)
            assertEquals(updatedRealm, updated.realm)
        }
    }

    @Test
    fun `given a repository with characters of multiple types, I can retrieve them one by one`() {
        runBlocking {
            val wowHardcoreEntity = basicWowHardcoreEntity.copy(id = 2)
            val lolEntity = basicLolEntity.copy(id = 3)
            repository.withState(
                EntitiesState(
                    listOf(basicWowEntity),
                    listOf(wowHardcoreEntity),
                    listOf(lolEntity)
                )
            )
            assertEquals(basicWowEntity, repository.get(basicWowEntity.id, Game.WOW))
            assertEquals(wowHardcoreEntity, repository.get(wowHardcoreEntity.id, Game.WOW_HC))
            assertEquals(lolEntity, repository.get(lolEntity.id, Game.LOL))
        }
    }

    @Test
    fun `given a repository of characters i can retrieve a character by a character or insert request`() {
        runBlocking {
            val wowHardcoreEntity = basicWowHardcoreEntity.copy(id = 2)
            val lolEntity = basicLolEntity.copy(id = 3)
            repository.withState(
                EntitiesState(
                    listOf(basicWowEntity),
                    listOf(wowHardcoreEntity),
                    listOf(lolEntity)
                )
            )

            val wowCharacterRequest: InsertEntityRequest =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            assertEquals(basicWowEntity, repository.get(wowCharacterRequest, Game.WOW))
            assertEquals(wowHardcoreEntity, repository.get(wowCharacterRequest, Game.WOW_HC))

            assertEquals(lolEntity, repository.get(LolEntityRequest("GTP ZeroMVPs", "WOW"), Game.LOL))
        }
    }

    @Test
    fun `given a repository with characters of multiple types, I can retrieve all of them`() {
        runBlocking {
            val hardcoreEntity = basicWowHardcoreEntity.copy(id = 3)
            val lolEntity = basicLolEntity.copy(id = 4)
            val repo = repository.withState(
                EntitiesState(
                    listOf(basicWowEntity, basicWowEntity2),
                    listOf(hardcoreEntity),
                    listOf(lolEntity)
                )
            )
            assertEquals(listOf(basicWowEntity, basicWowEntity2), repo.get(Game.WOW))
            assertEquals(listOf(hardcoreEntity), repo.get(Game.WOW_HC))
            assertEquals(listOf(lolEntity), repo.get(Game.LOL))
        }
    }

    @Test
    fun `getEntitiesOlderThan dispatches to the requested game`() {
        runBlocking {
            repository.withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val res = repository.getEntitiesOlderThan(Game.WOW, 30, Int.MAX_VALUE)
            assertEquals(listOf(basicWowEntity), res)
        }
    }

    @Test
    fun `given a repository with a character, I can delete it`() {
        runBlocking {
            repository.withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            repository.delete(basicWowEntity.id)
            assertEquals(listOf(), repository.state().wowEntities)
        }
    }

    @Test
    fun `given a repository with character present in views, I can retrieve those views`() {
        runBlocking {
            repository.withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val viewWithEntities = basicSimpleWowView.copy(entitiesIds = listOf(basicWowEntity.id))
            viewsRepository.withState(
                ViewsState(
                    listOf(viewWithEntities),
                    viewWithEntities.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") })
            )
            val views = repository.getViewsFromEntity(basicWowEntity.id, Game.WOW)
            assertEquals(listOf(basicSimpleWowView.id), views)
        }
    }
}

class EntitiesInMemoryRepositoryTest : EntitiesRepositoryTestCommon() {
    override val dataCacheRepository = DataCacheInMemoryRepository()
    override val viewsRepository = ViewsInMemoryRepository()
    override val repository = EntitiesInMemoryRepository(dataCacheRepository, viewsRepository)

    @BeforeEach
    fun beforeEach() {
        repository.clear()
        dataCacheRepository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntitiesDatabaseRepositoryTest : EntitiesRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = EntitiesDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
    override val dataCacheRepository = DataCacheDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
    override val viewsRepository = ViewsDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
