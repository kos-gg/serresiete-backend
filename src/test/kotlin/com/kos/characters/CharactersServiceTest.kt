package com.kos.characters

import arrow.core.Either
import com.kos.characters.CharactersTestHelper.basicGetAccountResponse
import com.kos.characters.CharactersTestHelper.basicGetPuuidResponse
import com.kos.characters.CharactersTestHelper.basicGetSummonerResponse
import com.kos.characters.CharactersTestHelper.basicLolCharacter
import com.kos.characters.CharactersTestHelper.basicWowCharacter
import com.kos.characters.CharactersTestHelper.gigaLolCharacterList
import com.kos.characters.CharactersTestHelper.gigaLolCharacterRequestList
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersState
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetPUUIDResponse
import com.kos.clients.domain.GetSummonerResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.hardcoreRealm
import com.kos.datacache.BlizzardMockHelper.notHardcoreRealm
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class CharactersServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `inserting two characters over an empty repository returns the ids of both new characters`() {
        runBlocking {
            val request1 =
                WowCharacterRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            val request2 = WowCharacterRequest("kakarøna", basicWowCharacter.region, basicWowCharacter.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(true)

            val charactersRepository = CharactersInMemoryRepository()
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1, 2)

            charactersService.createAndReturnIds(request, Game.WOW).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting two characters over an empty wow hardcore repository returns the ids of both new characters`() {
        runBlocking {
            val request1 =
                WowCharacterRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            val request2 = WowCharacterRequest("kakarøna", basicWowCharacter.region, basicWowCharacter.realm)

            `when`(blizzardClient.getCharacterProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getCharacterProfile(request2.region, request2.realm, request2.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request2)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(hardcoreRealm))
            `when`(blizzardClient.getRealm(request2.region, 5220)).thenReturn(Either.Right(hardcoreRealm))

            val charactersRepository = CharactersInMemoryRepository()
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1, 2)

            charactersService.createAndReturnIds(request, Game.WOW_HC).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting a character from a non hardcore realm does not get inserted over an empty wow hardcore repository`() {
        runBlocking {
            val request1 =
                WowCharacterRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)

            `when`(blizzardClient.getCharacterProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(notHardcoreRealm))

            val charactersRepository = CharactersInMemoryRepository()
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1)
            val expected: List<Long> = listOf()

            charactersService.createAndReturnIds(request, Game.WOW_HC)
                .fold({ fail(it.message) }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting a character that does not exist does not get inserted`() {
        runBlocking {
            val request1 =
                WowCharacterRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            val request2 = WowCharacterRequest("kakarøna", basicWowCharacter.region, basicWowCharacter.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(false)

            val charactersRepository = CharactersInMemoryRepository()
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1)
            charactersService.createAndReturnIds(request, Game.WOW).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting a lof of characters where half exists half doesn't`() {
        runBlocking {
            val state = CharactersState(listOf(), listOf(), gigaLolCharacterList)

            `when`(riotClient.getPUUIDByRiotId(anyString(), anyString())).thenAnswer { invocation ->
                val name = invocation.getArgument<String>(0)
                val tag = invocation.getArgument<String>(1)
                Either.Right(GetPUUIDResponse(UUID.randomUUID().toString(), name, tag))
            }
            `when`(riotClient.getSummonerByPuuid(anyString())).thenAnswer { invocation ->
                val puuid = invocation.getArgument<String>(0)
                Either.Right(
                    GetSummonerResponse(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        puuid,
                        10,
                        10L,
                        200
                    )
                )
            }

            val charactersRepository = CharactersInMemoryRepository().withState(state)
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val createAndReturnIds = charactersService.createAndReturnIds(gigaLolCharacterRequestList, Game.LOL)
            val expectedReturnedIds = listOf<Long>(7, 8, 9, 10, 11, 12, 13, 0, 1, 2, 3, 4, 5, 6)

            createAndReturnIds.fold({ fail() }) { assertEquals(expectedReturnedIds, it) }
        }
    }

    @Test
    fun `it should skip inserting same league character even if he changed his name`() {
        runBlocking {

            val state = CharactersState(listOf(), listOf(), listOf(basicLolCharacter))
            val request = LolCharacterRequest("R7 Disney Girl", "EUW")

            `when`(riotClient.getPUUIDByRiotId("R7 Disney Girl", "EUW")).thenReturn(Either.Right(basicGetPuuidResponse))
            `when`(riotClient.getSummonerByPuuid("1")).thenReturn(Either.Right(basicGetSummonerResponse))

            val charactersRepository = CharactersInMemoryRepository().withState(state)
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val createAndReturnIds = charactersService.createAndReturnIds(listOf(request), Game.LOL)
            createAndReturnIds.fold({ fail() }) { assertEquals(listOf(), it) }
        }
    }

    @Test
    fun `i can get a wow character`() {
        runBlocking {
            val charactersRepository =
                CharactersInMemoryRepository().withState(CharactersState(listOf(basicWowCharacter), listOf(), listOf()))
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(basicWowCharacter, charactersService.get(basicWowCharacter.id, Game.WOW))
        }
    }

    @Test
    fun `i can get a lol character`() {
        runBlocking {
            val charactersRepository =
                CharactersInMemoryRepository().withState(CharactersState(listOf(), listOf(), listOf(basicLolCharacter)))
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(basicLolCharacter, charactersService.get(basicLolCharacter.id, Game.LOL))
        }
    }

    @Test
    fun `i can get all wow characters`() {
        runBlocking {
            val charactersRepository =
                CharactersInMemoryRepository().withState(
                    CharactersState(
                        listOf(basicWowCharacter),
                        listOf(),
                        listOf(basicLolCharacter)
                    )
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(listOf(basicWowCharacter), charactersService.get(Game.WOW))
        }
    }

    @Test
    fun `i can get all lol characters`() {
        runBlocking {
            val charactersRepository =
                CharactersInMemoryRepository().withState(
                    CharactersState(
                        listOf(basicWowCharacter),
                        listOf(),
                        listOf(basicLolCharacter)
                    )
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(listOf(basicLolCharacter), charactersService.get(Game.LOL))
        }
    }

    @Test
    fun `i can update lol characters`() {
        runBlocking {
            val charactersRepository =
                CharactersInMemoryRepository().withState(CharactersState(listOf(), listOf(), listOf(basicLolCharacter)))
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            `when`(riotClient.getSummonerByPuuid(basicLolCharacter.puuid)).thenReturn(
                Either.Right(
                    basicGetSummonerResponse
                )
            )
            `when`(riotClient.getAccountByPUUID(basicLolCharacter.puuid)).thenReturn(
                Either.Right(
                    basicGetAccountResponse
                )
            )

            val res = charactersService.updateLolCharacters(listOf(basicLolCharacter))
            assertEquals(listOf(), res)
        }
    }
}