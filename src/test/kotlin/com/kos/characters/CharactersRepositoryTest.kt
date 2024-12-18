package com.kos.characters

import com.kos.characters.CharactersTestHelper.basicLolCharacter
import com.kos.characters.CharactersTestHelper.basicLolCharacterEnrichedRequest
import com.kos.characters.CharactersTestHelper.basicWowCharacter
import com.kos.characters.CharactersTestHelper.basicWowCharacter2
import com.kos.characters.CharactersTestHelper.basicWowRequest
import com.kos.characters.CharactersTestHelper.basicWowRequest2
import com.kos.characters.CharactersTestHelper.emptyCharactersState
import com.kos.characters.CharactersTestHelper.gigaLolCharacterList
import com.kos.characters.repository.CharactersDatabaseRepository
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersRepository
import com.kos.characters.repository.CharactersState
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
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

abstract class CharactersRepositoryTestCommon {

    abstract val repository: CharactersRepository
    abstract val dataCacheRepository: DataCacheRepository

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
            val expected = listOf(basicWowCharacter)
            repository.insert(listOf(basicWowRequest), Game.WOW_HC)
                .fold({ fail(it.message) }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given an empty repository i can insert lol characters`() {
        runBlocking {
            val expected = listOf(basicLolCharacter)
            repository.insert(listOf(basicLolCharacterEnrichedRequest), Game.LOL)
                .fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given an empty repository inserting a wow character that already exists fails`() {
        runBlocking {
            val character = WowCharacterRequest(
                basicWowCharacter.name,
                basicWowCharacter.region,
                basicWowCharacter.realm
            )

            val initialState = repository.state()
            assertEquals(emptyCharactersState, initialState)
            assertTrue(repository.insert(listOf(character, character), Game.WOW).isLeft())

            val finalState = repository.state()
            assertEquals(emptyCharactersState, finalState)
        }
    }

    @Test
    fun `given a repository that includes a wow character, adding the same one fails`() {
        runBlocking {
            val repo =
                repository.withState(CharactersState(listOf(basicWowCharacter, basicWowCharacter2), listOf(), listOf()))
            assertTrue(repo.insert(listOf(basicWowRequest), Game.WOW).isLeft())
            assertEquals(
                CharactersState(listOf(basicWowCharacter, basicWowCharacter2), listOf(), listOf()),
                repository.state()
            )
        }
    }

    @Test
    fun `given a repository with characters of multiple types, I can retrieve them one by one`() {
        runBlocking {
            val repo =
                repository.withState(
                    CharactersState(
                        listOf(basicWowCharacter),
                        listOf(basicWowCharacter),
                        listOf(basicLolCharacter)
                    )
                )
            assertEquals(basicWowCharacter, repo.get(basicWowCharacter.id, Game.WOW))
            assertEquals(basicWowCharacter, repo.get(basicWowCharacter.id, Game.WOW_HC))
            assertEquals(basicLolCharacter, repo.get(basicLolCharacter.id, Game.LOL))
        }
    }

    @Test
    fun `given a repository of characters i can retrieve a character by a character request`() {
        runBlocking {
            val repo = repository.withState(
                CharactersState(
                    listOf(basicWowCharacter),
                    listOf(basicWowCharacter),
                    gigaLolCharacterList
                )
            )

            val wowCharacterRequest: CharacterCreateRequest =
                WowCharacterRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            assertEquals(basicWowCharacter, repo.get(wowCharacterRequest, Game.WOW))
            assertEquals(basicWowCharacter, repo.get(wowCharacterRequest, Game.WOW_HC))

            val lolCharacter = gigaLolCharacterList[3]
            val lolCharacterRequest = LolCharacterRequest(lolCharacter.name, lolCharacter.tag)
            assertEquals(lolCharacter, repo.get(lolCharacterRequest, Game.LOL))
        }
    }

    @Test
    fun `given a repository of characters i can retrieve a character by a character insert`() {
        runBlocking {
            val repo = repository.withState(
                CharactersState(
                    listOf(basicWowCharacter),
                    listOf(basicWowCharacter),
                    gigaLolCharacterList
                )
            )

            val wowCharacterRequest: CharacterInsertRequest =
                WowCharacterRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            assertEquals(basicWowCharacter, repo.get(wowCharacterRequest, Game.WOW))
            assertEquals(basicWowCharacter, repo.get(wowCharacterRequest, Game.WOW_HC))

            val lolCharacter = gigaLolCharacterList[3]
            val lolCharacterRequest = LolCharacterEnrichedRequest(
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
                CharactersState(
                    listOf(basicWowCharacter, basicWowCharacter2),
                    listOf(basicWowCharacter2),
                    listOf(basicLolCharacter)
                )
            )
            assertEquals(listOf(basicWowCharacter, basicWowCharacter2), repo.get(Game.WOW))
            assertEquals(listOf(basicWowCharacter2), repo.get(Game.WOW_HC))
            assertEquals(listOf(basicLolCharacter), repo.get(Game.LOL))
        }
    }

    @Test
    fun `given an empty repository, I can't insert characters when game does not match`() {
        runBlocking {
            assertTrue(repository.insert(listOf(basicLolCharacterEnrichedRequest), Game.WOW).isLeft())
            assertTrue(repository.insert(listOf(basicWowRequest), Game.LOL).isLeft())
            assertEquals(emptyCharactersState, repository.state())
        }
    }

    @Test
    fun `given a repository with wow characters, I can insert more`() {
        runBlocking {
            val repositoryWithState =
                repository.withState(CharactersState(listOf(basicWowCharacter), listOf(), listOf()))
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
                repository.withState(CharactersState(listOf(), listOf(), listOf(basicLolCharacter)))
            val request =
                basicLolCharacterEnrichedRequest.copy(puuid = "different-puuid", summonerId = "different-summoner-id")
            val inserted = repositoryWithState.insert(listOf(request), Game.LOL)
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(2), characters.map { it.id }) }
                .onLeft { fail(it.message) }
        }
    }

    @Test
    fun `i can insert a lol character with a tag longer than 3 characters`() {
        runBlocking {
            val request = basicLolCharacterEnrichedRequest.copy(tag = "12345")
            val inserted = repository.insert(listOf(request), Game.LOL)
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(1), characters.map { it.id }) }
                .onLeft { fail(it.message) }
        }
    }

    @Test
    fun `given a repository with a lol character, i can update it`() {
        runBlocking {
            val repoWithState = repository.withState(CharactersState(listOf(), listOf(), listOf(basicLolCharacter)))
            val updatedName = "Marcnute"
            val updatedTag = "EUW"
            val updatedSummonerIconId = 10
            val updatedSummonerLevel = 500
            val request = LolCharacterEnrichedRequest(
                updatedName,
                updatedTag,
                basicLolCharacter.puuid,
                updatedSummonerIconId,
                basicLolCharacter.summonerId,
                updatedSummonerLevel
            )
            val update = repoWithState.update(1, request, Game.LOL)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().lolCharacters.first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedTag, updated.tag)
            assertEquals(updatedSummonerIconId, updated.summonerIcon)
            assertEquals(updatedSummonerLevel, updated.summonerLevel)
            assertEquals(basicLolCharacter.puuid, updated.puuid)
            assertEquals(basicLolCharacter.summonerId, updated.summonerId)
        }
    }

    @Test
    fun `given a repository with a wow character, i can update it`() {
        runBlocking {
            val repoWithState = repository.withState(CharactersState(listOf(basicWowCharacter), listOf(), listOf()))
            val updatedName = "camilo"
            val updatedRegion = "eu"
            val updatedRealm = "stitches"
            val request = WowCharacterRequest(
                updatedName,
                updatedRegion,
                updatedRealm
            )
            val update = repoWithState.update(1, request, Game.WOW)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().wowCharacters.first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedRegion, updated.region)
            assertEquals(updatedRealm, updated.realm)
        }
    }

    @Test
    fun `given a repository with a wow hardcore character, i can update it`() {
        runBlocking {
            val repoWithState = repository.withState(CharactersState(listOf(), listOf(basicWowCharacter), listOf()))
            val updatedName = "camilo"
            val updatedRegion = "eu"
            val updatedRealm = "stitches"
            val request = WowCharacterRequest(
                updatedName,
                updatedRegion,
                updatedRealm
            )
            val update = repoWithState.update(1, request, Game.WOW_HC)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().wowHardcoreCharacters.first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedRegion, updated.region)
            assertEquals(updatedRealm, updated.realm)
        }
    }

    @Test
    fun `get characters to sync should return those characters who don't have a cached record or were cached before olderThanMinutes`() {
        runBlocking {
            val lolCharacters = (1..3).map {
                LolCharacter(
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
                CharactersState(
                    listOf(),
                    listOf(),
                    lolCharacters
                )
            )

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now(), Game.LOL),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.LOL)
                )
            )
            val res = repoWithState.getCharactersToSync(Game.LOL, 30)

            assertEquals(listOf<Long>(2, 3), res.map { it.id })
        }
    }

    @Test
    fun `get characters to sync should return all characters if all records were cached before olderThanMinutes`() {
        runBlocking {
            val lolCharacters = (1..3).map {
                LolCharacter(
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
                CharactersState(
                    listOf(),
                    listOf(),
                    lolCharacters
                )
            )

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(3, "", OffsetDateTime.now().minusMinutes(31), Game.LOL)
                )
            )
            val res = repoWithState.getCharactersToSync(Game.LOL, 30)

            assertEquals(setOf<Long>(1, 2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `get characters to sync should return all characters if there's no cached records`() {
        runBlocking {
            val lolCharacters = (1..3).map {
                LolCharacter(
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
                CharactersState(
                    listOf(),
                    listOf(),
                    lolCharacters
                )
            )

            val res = repoWithState.getCharactersToSync(Game.LOL, 30)

            assertEquals(setOf<Long>(1, 2, 3), res.map { it.id }.toSet())
        }
    }

    @Test
    fun `get characters to sync should return no characters if they have been cached recently even if they have an old cached record`() {
        runBlocking {
            val repoWithState = repository.withState(
                CharactersState(
                    listOf(),
                    listOf(),
                    listOf(basicLolCharacter)
                )
            )

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now().minusMinutes(31), Game.LOL),
                    DataCache(1, "", OffsetDateTime.now(), Game.LOL)
                )
            )
            val res = repoWithState.getCharactersToSync(Game.LOL, 30)

            assertEquals(listOf(), res.map { it.id })
        }
    }


}

class CharactersInMemoryRepositoryTest : CharactersRepositoryTestCommon() {
    override val dataCacheRepository = DataCacheInMemoryRepository()
    override val repository = CharactersInMemoryRepository(dataCacheRepository)

    @BeforeEach
    fun beforeEach() {
        repository.clear()
        dataCacheRepository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CharactersDatabaseRepositoryTest : CharactersRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = CharactersDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
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
