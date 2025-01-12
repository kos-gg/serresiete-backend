package com.kos.entities

import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowCharacter
import com.kos.entities.EntitiesTestHelper.basicWowEntity2
import com.kos.entities.EntitiesTestHelper.basicWowEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.EntitiesTestHelper.gigaLolEntityList
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.EntitiesState
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.views.Game
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.repository.ViewsDatabaseRepository
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsRepository
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

abstract class EntitiesRepositoryTestCommon {

    abstract val repository: EntitiesRepository
    abstract val dataCacheRepository: DataCacheRepository
    abstract val viewsRepository: ViewsRepository

    @Test
    fun `given an empty repository i can insert wow characters`() {
        runBlocking {
            val expected = listOf(basicWowCharacter)
            repository.insert(listOf(basicWowRequest), Game.WOW).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given an empty repository i can insert wow hardcore characters`() {
        runBlocking {
            val expected = listOf(basicWowHardcoreEntity)
            repository.insert(listOf(basicWowEnrichedRequest), Game.WOW_HC)
                .fold({ fail(it.message) }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given an empty repository i can insert lol characters`() {
        runBlocking {
            val expected = listOf(basicLolEntity)
            repository.insert(listOf(basicLolEntityEnrichedRequest), Game.LOL)
                .fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given an empty repository inserting a wow character that already exists fails`() {
        runBlocking {
            val character = WowEntityRequest(
                basicWowCharacter.name,
                basicWowCharacter.region,
                basicWowCharacter.realm
            )

            val initialState = repository.state()
            assertEquals(emptyEntitiesState, initialState)
            assertTrue(repository.insert(listOf(character, character), Game.WOW).isLeft())

            val finalState = repository.state()
            assertEquals(emptyEntitiesState, finalState)
        }
    }

    @Test
    fun `given a repository that includes a wow character, adding the same one fails`() {
        runBlocking {
            val repo =
                repository.withState(EntitiesState(listOf(basicWowCharacter, basicWowEntity2), listOf(), listOf()))
            assertTrue(repo.insert(listOf(basicWowRequest), Game.WOW).isLeft())
            assertEquals(
                EntitiesState(listOf(basicWowCharacter, basicWowEntity2), listOf(), listOf()),
                repository.state()
            )
        }
    }

    @Test
    fun `given a repository with characters of multiple types, I can retrieve them one by one`() {
        runBlocking {
            val repo =
                repository.withState(
                    EntitiesState(
                        listOf(basicWowCharacter),
                        listOf(basicWowHardcoreEntity),
                        listOf(basicLolEntity)
                    )
                )
            assertEquals(basicWowCharacter, repo.get(basicWowCharacter.id, Game.WOW))
            assertEquals(basicWowHardcoreEntity, repo.get(basicWowHardcoreEntity.id, Game.WOW_HC))
            assertEquals(basicLolEntity, repo.get(basicLolEntity.id, Game.LOL))
        }
    }

    @Test
    fun `given a repository of characters i can retrieve a character by a character request`() {
        runBlocking {
            val repo = repository.withState(
                EntitiesState(
                    listOf(basicWowCharacter),
                    listOf(basicWowHardcoreEntity),
                    gigaLolEntityList
                )
            )

            val wowCharacterRequest: CreateEntityRequest =
                WowEntityRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            assertEquals(basicWowCharacter, repo.get(wowCharacterRequest, Game.WOW))
            assertEquals(basicWowHardcoreEntity, repo.get(wowCharacterRequest, Game.WOW_HC))

            val lolCharacter = gigaLolEntityList[3]
            val lolCharacterRequest = LolEntityRequest(lolCharacter.name, lolCharacter.tag)
            assertEquals(lolCharacter, repo.get(lolCharacterRequest, Game.LOL))
        }
    }

    @Test
    fun `given a repository of characters i can retrieve a character by a character insert`() {
        runBlocking {
            val repo = repository.withState(
                EntitiesState(
                    listOf(basicWowCharacter),
                    listOf(basicWowHardcoreEntity),
                    gigaLolEntityList
                )
            )

            val wowCharacterRequest: InsertEntityRequest =
                WowEntityRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            assertEquals(basicWowCharacter, repo.get(wowCharacterRequest, Game.WOW))
            assertEquals(basicWowHardcoreEntity, repo.get(wowCharacterRequest, Game.WOW_HC))

            val lolCharacter = gigaLolEntityList[3]
            val lolCharacterRequest = LolEnrichedEntityRequest(
                lolCharacter.name,
                lolCharacter.tag,
                lolCharacter.puuid,
                lolCharacter.summonerIcon,
                lolCharacter.id.toString(),
                lolCharacter.summonerLevel
            )
            assertEquals(lolCharacter, repo.get(lolCharacterRequest, Game.LOL))
        }
    }

    @Test
    fun `given a repository with characters of multiple types, I can retrieve all of them`() {
        runBlocking {
            val repo = repository.withState(
                EntitiesState(
                    listOf(basicWowCharacter, basicWowEntity2),
                    listOf(basicWowHardcoreEntity),
                    listOf(basicLolEntity)
                )
            )
            assertEquals(listOf(basicWowCharacter, basicWowEntity2), repo.get(Game.WOW))
            assertEquals(listOf(basicWowHardcoreEntity), repo.get(Game.WOW_HC))
            assertEquals(listOf(basicLolEntity), repo.get(Game.LOL))
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
    fun `given a repository with wow characters, I can insert more`() {
        runBlocking {
            val repositoryWithState =
                repository.withState(EntitiesState(listOf(basicWowCharacter), listOf(), listOf()))
            val inserted = repositoryWithState.insert(listOf(basicWowRequest2), Game.WOW)
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(2), characters.map { it.id }) }
                .onLeft { fail(it.message) }
        }
    }

    @Test
    fun `given a repository with lol characters, I can insert more`() {
        runBlocking {
            val repositoryWithState =
                repository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val request =
                basicLolEntityEnrichedRequest.copy(puuid = "different-puuid", summonerId = "different-summoner-id")
            val inserted = repositoryWithState.insert(listOf(request), Game.LOL)
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(2), characters.map { it.id }) }
                .onLeft { fail(it.message) }
        }
    }

    @Test
    fun `i can insert a lol character with a tag longer than 3 characters`() {
        runBlocking {
            val request = basicLolEntityEnrichedRequest.copy(tag = "12345")
            val inserted = repository.insert(listOf(request), Game.LOL)
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(1), characters.map { it.id }) }
                .onLeft { fail(it.message) }
        }
    }

    @Test
    fun `given a repository with a lol character, i can update it`() {
        runBlocking {
            val repoWithState = repository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val updatedName = "Marcnute"
            val updatedTag = "EUW"
            val updatedSummonerIconId = 10
            val updatedSummonerLevel = 500
            val request = LolEnrichedEntityRequest(
                updatedName,
                updatedTag,
                basicLolEntity.puuid,
                updatedSummonerIconId,
                basicLolEntity.summonerId,
                updatedSummonerLevel
            )
            val update = repoWithState.update(1, request, Game.LOL)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().lolEntities.first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedTag, updated.tag)
            assertEquals(updatedSummonerIconId, updated.summonerIcon)
            assertEquals(updatedSummonerLevel, updated.summonerLevel)
            assertEquals(basicLolEntity.puuid, updated.puuid)
            assertEquals(basicLolEntity.summonerId, updated.summonerId)
        }
    }

    @Test
    fun `given a repository with a wow character, i can update it`() {
        runBlocking {
            val repoWithState = repository.withState(EntitiesState(listOf(basicWowCharacter), listOf(), listOf()))
            val updatedName = "camilo"
            val updatedRegion = "eu"
            val updatedRealm = "stitches"
            val request = WowEntityRequest(
                updatedName,
                updatedRegion,
                updatedRealm
            )
            val update = repoWithState.update(1, request, Game.WOW)
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
    fun `given a repository with a wow hardcore character, i can update it`() {
        runBlocking {
            val repoWithState = repository.withState(EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))
            val updatedName = "camilo"
            val updatedRegion = "eu"
            val updatedRealm = "stitches"
            val request = WowEntityRequest(
                updatedName,
                updatedRegion,
                updatedRealm
            )
            val update = repoWithState.update(1, request, Game.WOW_HC)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().wowHardcoreEntities.first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedRegion, updated.region)
            assertEquals(updatedRealm, updated.realm)
        }
    }

    @Test
    fun `get characters to sync should return those characters who don't have a cached record or were cached before olderThanMinutes`() {
        runBlocking {
            val lolEntities = (1..3).map {
                LolEntity(
                    it.toLong(),
                    it.toString(),
                    it.toString(),
                    it.toString(),
                    it,
                    it.toString(),
                    it
                )
            }
            val repoWithState = repository.withState(
                EntitiesState(
                    listOf(),
                    listOf(),
                    lolEntities
                )
            )

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now(), Game.LOL),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.LOL)
                )
            )
            val res = repoWithState.getEntitiesToSync(Game.LOL, 30)

            assertEquals(listOf<Long>(2, 3), res.map { it.id })
        }
    }

    @Test
    fun `get characters to sync should return all characters if all records were cached before olderThanMinutes`() {
        runBlocking {
            val lolEntities = (1..3).map {
                LolEntity(
                    it.toLong(),
                    it.toString(),
                    it.toString(),
                    it.toString(),
                    it,
                    it.toString(),
                    it
                )
            }
            val repoWithState = repository.withState(
                EntitiesState(
                    listOf(),
                    listOf(),
                    lolEntities
                )
            )

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(3, "", OffsetDateTime.now().minusMinutes(31), Game.LOL)
                )
            )
            val res = repoWithState.getEntitiesToSync(Game.LOL, 30)

            assertEquals(setOf<Long>(1, 2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `get characters to sync should return all characters if there's no cached records`() {
        runBlocking {
            val lolEntities = (1..3).map {
                LolEntity(
                    it.toLong(),
                    it.toString(),
                    it.toString(),
                    it.toString(),
                    it,
                    it.toString(),
                    it
                )
            }
            val repoWithState = repository.withState(
                EntitiesState(
                    listOf(),
                    listOf(),
                    lolEntities
                )
            )

            val res = repoWithState.getEntitiesToSync(Game.LOL, 30)

            assertEquals(setOf<Long>(1, 2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `get characters to sync should return no characters if they have been cached recently even if they have an old cached record`() {
        runBlocking {
            val repoWithState = repository.withState(
                EntitiesState(
                    listOf(),
                    listOf(),
                    listOf(basicLolEntity)
                )
            )

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(1, "", OffsetDateTime.now(), Game.LOL)
                )
            )
            val res = repoWithState.getEntitiesToSync(Game.LOL, 30)

            assertEquals(listOf(), res.map { it.id })
        }
    }

    @Test
    fun `given a repository with a character, I can delete it`() {
        runBlocking {
            repository.withState(EntitiesState(listOf(basicWowCharacter), listOf(), listOf()))
            repository.delete(basicWowCharacter.id, Game.WOW)
            assertEquals(listOf(), repository.state().wowEntities)
        }
    }

    @Test
    fun `given a repository with character present i views, I can retrieve those views`() {
        runBlocking {
            repository.withState(EntitiesState(listOf(basicWowCharacter), listOf(), listOf()))
            viewsRepository.withState(listOf(basicSimpleWowView.copy(entitiesIds = listOf(basicWowCharacter.id))))
            val views = repository.getViewsFromEntity(basicWowCharacter.id, Game.WOW)
            assertEquals(listOf(basicSimpleWowView.id),views)
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
