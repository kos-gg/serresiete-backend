package com.kos.entities

import arrow.core.Either
import com.kos.entities.EntitiesTestHelper.basicGetAccountResponse
import com.kos.entities.EntitiesTestHelper.basicGetPuuidResponse
import com.kos.entities.EntitiesTestHelper.basicGetSummonerResponse
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicWowCharacter
import com.kos.entities.EntitiesTestHelper.gigaLolEntityList
import com.kos.entities.EntitiesTestHelper.gigaLolCharacterRequestList
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
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

class EntitiesServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `inserting two characters over an empty repository returns the ids of both new characters`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowCharacter.region, basicWowCharacter.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(true)

            val charactersRepository = EntitiesInMemoryRepository()
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1, 2)

            entitiesService.createAndReturnIds(request, Game.WOW).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting two characters over an empty wow hardcore repository returns the ids of both new characters`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowCharacter.region, basicWowCharacter.realm)

            `when`(blizzardClient.getCharacterProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getCharacterProfile(request2.region, request2.realm, request2.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request2)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(hardcoreRealm))
            `when`(blizzardClient.getRealm(request2.region, 5220)).thenReturn(Either.Right(hardcoreRealm))

            val charactersRepository = EntitiesInMemoryRepository()
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1, 2)

            entitiesService.createAndReturnIds(request, Game.WOW_HC).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting a character from a non hardcore realm does not get inserted over an empty wow hardcore repository`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)

            `when`(blizzardClient.getCharacterProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(notHardcoreRealm))

            val charactersRepository = EntitiesInMemoryRepository()
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1)
            val expected: List<Long> = listOf()

            entitiesService.createAndReturnIds(request, Game.WOW_HC)
                .fold({ fail(it.message) }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting a character that does not exist does not get inserted`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowCharacter.name, basicWowCharacter.region, basicWowCharacter.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowCharacter.region, basicWowCharacter.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(false)

            val charactersRepository = EntitiesInMemoryRepository()
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1)
            entitiesService.createAndReturnIds(request, Game.WOW).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting a lof of characters where half exists half doesn't`() {
        runBlocking {
            val state = EntitiesState(listOf(), listOf(), gigaLolEntityList)

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

            val charactersRepository = EntitiesInMemoryRepository().withState(state)
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val createAndReturnIds = entitiesService.createAndReturnIds(gigaLolCharacterRequestList, Game.LOL)
            val expectedReturnedIds = listOf<Long>(7, 8, 9, 10, 11, 12, 13, 0, 1, 2, 3, 4, 5, 6)

            createAndReturnIds.fold({ fail() }) { assertEquals(expectedReturnedIds, it) }
        }
    }

    @Test
    fun `it should skip inserting same league character even if he changed his name`() {
        runBlocking {

            val state = EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
            val request = LolEntityRequest("R7 Disney Girl", "EUW")

            `when`(riotClient.getPUUIDByRiotId("R7 Disney Girl", "EUW")).thenReturn(Either.Right(basicGetPuuidResponse))
            `when`(riotClient.getSummonerByPuuid("1")).thenReturn(Either.Right(basicGetSummonerResponse))

            val charactersRepository = EntitiesInMemoryRepository().withState(state)
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val createAndReturnIds = entitiesService.createAndReturnIds(listOf(request), Game.LOL)
            createAndReturnIds.fold({ fail() }) { assertEquals(listOf(), it) }
        }
    }

    @Test
    fun `i can get a wow character`() {
        runBlocking {
            val charactersRepository =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(basicWowCharacter), listOf(), listOf()))
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(basicWowCharacter, entitiesService.get(basicWowCharacter.id, Game.WOW))
        }
    }

    @Test
    fun `i can get a lol character`() {
        runBlocking {
            val charactersRepository =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(basicLolEntity, entitiesService.get(basicLolEntity.id, Game.LOL))
        }
    }

    @Test
    fun `i can get all wow characters`() {
        runBlocking {
            val charactersRepository =
                EntitiesInMemoryRepository().withState(
                    EntitiesState(
                        listOf(basicWowCharacter),
                        listOf(),
                        listOf(basicLolEntity)
                    )
                )
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(listOf(basicWowCharacter), entitiesService.get(Game.WOW))
        }
    }

    @Test
    fun `i can get all lol characters`() {
        runBlocking {
            val charactersRepository =
                EntitiesInMemoryRepository().withState(
                    EntitiesState(
                        listOf(basicWowCharacter),
                        listOf(),
                        listOf(basicLolEntity)
                    )
                )
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(listOf(basicLolEntity), entitiesService.get(Game.LOL))
        }
    }

    @Test
    fun `i can update lol characters`() {
        runBlocking {
            val charactersRepository =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid)).thenReturn(
                Either.Right(
                    basicGetSummonerResponse
                )
            )
            `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid)).thenReturn(
                Either.Right(
                    basicGetAccountResponse
                )
            )

            val res = entitiesService.updateLolEntities(listOf(basicLolEntity))
            assertEquals(listOf(), res)
        }
    }
}