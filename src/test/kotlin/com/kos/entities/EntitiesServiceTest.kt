package com.kos.entities

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetPUUIDResponse
import com.kos.clients.domain.GetSummonerResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.hardcoreRealm
import com.kos.datacache.BlizzardMockHelper.notHardcoreRealm
import com.kos.entities.EntitiesTestHelper.basicGetAccountResponse
import com.kos.entities.EntitiesTestHelper.basicGetPuuidResponse
import com.kos.entities.EntitiesTestHelper.basicGetSummonerResponse
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntity2
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.gigaLolCharacterRequestList
import com.kos.entities.EntitiesTestHelper.gigaLolEntityList
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
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
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(true)

            val charactersRepository = EntitiesInMemoryRepository()
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1, 2)

            entitiesService.createAndReturnIds(request, Game.WOW)
                .fold({ fail() }) { res -> assertEquals(expected, res.map { it.first.id }) }
        }
    }

    @Test
    fun `inserting two characters over an empty wow hardcore repository returns the ids of both new characters`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

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

            entitiesService.createAndReturnIds(request, Game.WOW_HC)
                .fold({ fail() }) { res -> assertEquals(expected, res.map { it.first.id }) }
        }
    }

    @Test
    fun `inserting a character from a non hardcore realm does not get inserted over an empty wow hardcore repository`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)

            `when`(blizzardClient.getCharacterProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(notHardcoreRealm))

            val charactersRepository = EntitiesInMemoryRepository()
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1)
            val expected: List<Long> = listOf()

            entitiesService.createAndReturnIds(request, Game.WOW_HC)
                .fold({ fail(it.message) }) { res -> assertEquals(expected, res.map { it.first.id }) }
        }
    }

    @Test
    fun `inserting a character that does not exist does not get inserted`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(false)

            val charactersRepository = EntitiesInMemoryRepository()
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1)
            entitiesService.createAndReturnIds(request, Game.WOW)
                .fold({ fail() }) { res -> assertEquals(expected, res.map { it.first.id }) }
        }
    }

    @Test
    fun `inserting a character with same blizzard id does not get inserted`() {
        runBlocking {
            val request = WowEntityRequest(basicWowHardcoreEntity.name, basicWowHardcoreEntity.region, basicWowHardcoreEntity.realm)

            `when`(blizzardClient.getCharacterProfile(basicWowHardcoreEntity.region, basicWowHardcoreEntity.realm, basicWowHardcoreEntity.name))
                .thenReturn(BlizzardMockHelper.getCharacterProfile(request)
                    .map { it.copy(id = basicWowHardcoreEntity.blizzardId ?: 12345) })
            `when`(blizzardClient.getRealm(request.region, 5220)).thenReturn(Either.Right(hardcoreRealm))

            val charactersRepository = EntitiesInMemoryRepository().withState(
                EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val expected: List<Long> = listOf(1)
            entitiesService.createAndReturnIds(listOf(request), Game.WOW_HC)
                .fold({ fail() }) { res -> assertEquals(expected, res.map { it.first.id }) }
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

            createAndReturnIds.fold({ fail() }) { res -> assertEquals(expectedReturnedIds, res.map { it.first.id }) }
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
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(basicWowEntity, entitiesService.get(basicWowEntity.id, Game.WOW))
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
                        listOf(basicWowEntity),
                        listOf(),
                        listOf(basicLolEntity)
                    )
                )
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            assertEquals(listOf(basicWowEntity), entitiesService.get(Game.WOW))
        }
    }

    @Test
    fun `i can get all lol characters`() {
        runBlocking {
            val charactersRepository =
                EntitiesInMemoryRepository().withState(
                    EntitiesState(
                        listOf(basicWowEntity),
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

    @Test
    fun `given a request of create entity return a list of pairs with Ids and the alias propagated`() {
        runBlocking {
            val alias = "kako"
            val alias2 = "sancho"
            val charactersRepository = EntitiesInMemoryRepository().withState(
                EntitiesState(
                    listOf(),
                    listOf(),
                    listOf(basicLolEntity)
                )
            )
            val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val request = LolEntityRequest(basicLolEntity.name, basicLolEntity.tag, alias)
            val requestNotInState = LolEntityRequest(basicLolEntity2.name, basicLolEntity2.tag, alias2)

            `when`(riotClient.getPUUIDByRiotId(basicLolEntity2.name, basicLolEntity2.tag)).thenReturn(
                Either.Right(
                    GetPUUIDResponse(basicLolEntity2.puuid, basicLolEntity2.name, basicLolEntity2.tag)
                )
            )

            `when`(riotClient.getSummonerByPuuid(basicLolEntity2.puuid)).thenReturn(
                Either.Right(
                    GetSummonerResponse(
                        basicLolEntity2.puuid,
                        basicLolEntity2.summonerIcon,
                        1L,
                        basicLolEntity2.summonerLevel
                    )
                )
            )

            val result = entitiesService.createAndReturnIds(listOf(request, requestNotInState), Game.LOL)

            result.onLeft { fail(it.message) }
                .onRight { assertEquals(listOf(basicLolEntity2 to alias2, basicLolEntity to alias), it) }
        }
    }
}