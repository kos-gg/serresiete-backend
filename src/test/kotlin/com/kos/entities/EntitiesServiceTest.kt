package com.kos.entities

import arrow.core.Either
import com.kos.entities.EntitiesTestHelper.basicGetPuuidResponse
import com.kos.entities.EntitiesTestHelper.basicGetSummonerResponse
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity
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
import com.kos.entities.EntitiesTestHelper.basicGetAccountResponse
import com.kos.entities.EntitiesTestHelper.basicLolEntity2
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.entitiesResolvers.LolResolver
import com.kos.entities.entitiesResolvers.WowHardcoreResolver
import com.kos.entities.entitiesResolvers.WowResolver
import com.kos.entities.entitiesUpdaters.LolUpdater
import com.kos.entities.entitiesUpdaters.WowHardcoreGuildUpdater
import com.kos.entities.repository.WowGuildsInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
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
    fun `resolving two characters over an empty repository resolves both as new`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(true)


            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1, request2)
            val expected = ResolvedEntities(
                entities = listOf(request1 to null, request2 to null),
                existing = listOf(),
                guild = null,
            )

            entitiesService.resolveEntities(request, Game.WOW)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving two characters over an empty wow hardcore repository resolves both as new`() {
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

            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1, request2)

            val expected = ResolvedEntities(
                entities = listOf(request1 to null, request2 to null),
                existing = listOf(),
                guild = null,
            )

            entitiesService.resolveEntities(request, Game.WOW_HC)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a character from a non hardcore realm does not get inserted over an empty wow hardcore repository`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)

            `when`(blizzardClient.getCharacterProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(notHardcoreRealm))

            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1)
            val expected = ResolvedEntities(
                listOf(),
                listOf(),
                null
            )

            entitiesService.resolveEntities(request, Game.WOW_HC)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a character that does not exist does not get inserted`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(false)

            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1, request2)
            val expected = ResolvedEntities(
                listOf(request1 to null),
                listOf(),
                null
            )

            entitiesService.resolveEntities(request, Game.WOW)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a character with same blizzard id does not get inserted`() {
        runBlocking {
            val request = WowEntityRequest(
                basicWowHardcoreEntity.name,
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm
            )

            `when`(
                blizzardClient.getCharacterProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            )
                .thenReturn(
                    BlizzardMockHelper.getCharacterProfile(request)
                        .map { it.copy(id = basicWowHardcoreEntity.blizzardId ?: 12345) })
            `when`(blizzardClient.getRealm(request.region, 5220)).thenReturn(Either.Right(hardcoreRealm))

            val initialState = EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf())

            val entitiesService = createService(initialState)

            val expected = ResolvedEntities(
                listOf(),
                listOf(basicWowHardcoreEntity to null),
                null
            )
            entitiesService.resolveEntities(listOf(request), Game.WOW_HC)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a lof of characters where half exists half doesn't must split them correctly`() {
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

            val entitiesService = createService(state)
            val resolvedEntities = entitiesService.resolveEntities(gigaLolCharacterRequestList, Game.LOL)
            val expected = ResolvedEntities(
                entities = listOf(
                    LolEnrichedEntityRequest(
                        name = "sanxei7",
                        tag = "euw7",
                        puuid = "be5213a7-4546-4a94-86db-3f607cf0fa04",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei8",
                        tag = "euw8",
                        puuid = "7cf45999-05ef-4473-b245-2028e4169111",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei9",
                        tag = "euw9",
                        puuid = "262b2d4e-ba6c-4e3b-a13c-e168b278164f",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei10",
                        tag = "euw10",
                        puuid = "2f06cb6f-d9ac-4962-84a5-ca77c5376711",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei11",
                        tag = "euw11",
                        puuid = "e1d0d3d1-69b2-41b5-9951-161132b52912",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei12",
                        tag = "euw12",
                        puuid = "45fc6752-d210-44fd-a02a-cb146fc9d8ec",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei13",
                        tag = "euw13",
                        puuid = "68b7f23a-5c9d-4904-82b2-409322dd6713",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null
                ),
                existing = listOf(
                    LolEntity(
                        id = 0,
                        name = "sanxei0",
                        tag = "euw0",
                        puuid = "0",
                        summonerIcon = 0,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 1,
                        name = "sanxei1",
                        tag = "euw1",
                        puuid = "1",
                        summonerIcon = 1,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 2,
                        name = "sanxei2",
                        tag = "euw2",
                        puuid = "2",
                        summonerIcon = 2,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 3,
                        name = "sanxei3",
                        tag = "euw3",
                        puuid = "3",
                        summonerIcon = 3,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 4,
                        name = "sanxei4",
                        tag = "euw4",
                        puuid = "4",
                        summonerIcon = 4,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 5,
                        name = "sanxei5",
                        tag = "euw5",
                        puuid = "5",
                        summonerIcon = 5,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 6,
                        name = "sanxei6",
                        tag = "euw6",
                        puuid = "6",
                        summonerIcon = 6,
                        summonerLevel = 400
                    ) to null
                ),
                guild = null
            )

            resolvedEntities
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `it should skip inserting same league character even if he changed his name`() {
        runBlocking {

            val state = EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
            val request = LolEntityRequest("R7 Disney Girl", "EUW")

            `when`(riotClient.getPUUIDByRiotId("R7 Disney Girl", "EUW")).thenReturn(Either.Right(basicGetPuuidResponse))
            `when`(riotClient.getSummonerByPuuid("1")).thenReturn(Either.Right(basicGetSummonerResponse))

            val entitiesService = createService(state)
            val expected = ResolvedEntities(
                listOf(),
                listOf(),
                null
            )

            val resolvedEntities = entitiesService.resolveEntities(listOf(request), Game.LOL)
            resolvedEntities
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `i can get a wow character`() {
        runBlocking {
            val initialState = EntitiesState(listOf(basicWowEntity), listOf(), listOf())

            val entitiesService = createService(initialState)

            assertEquals(basicWowEntity, entitiesService.get(basicWowEntity.id, Game.WOW))
        }
    }

    @Test
    fun `i can get a lol character`() {
        runBlocking {
            val initialState = EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
            val entitiesService = createService(initialState)

            assertEquals(basicLolEntity, entitiesService.get(basicLolEntity.id, Game.LOL))
        }
    }

    @Test
    fun `i can get all wow characters`() {
        runBlocking {
            val initialState = EntitiesState(
                listOf(basicWowEntity),
                listOf(),
                listOf(basicLolEntity)
            )

            val entitiesService = createService(initialState)
            assertEquals(listOf(basicWowEntity), entitiesService.get(Game.WOW))
        }
    }

    @Test
    fun `i can get all lol characters`() {
        runBlocking {
            val initialState = EntitiesState(
                listOf(basicWowEntity),
                listOf(),
                listOf(basicLolEntity)
            )

            val entitiesService = createService(initialState)
            assertEquals(listOf(basicLolEntity), entitiesService.get(Game.LOL))
        }
    }


    //TODO: Move wherever it belongs
    @Test
    fun `i can update lol characters`() {
        runBlocking {
            val initialState = EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
            val entitiesService = createService(initialState)
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

            val res = entitiesService.updateEntities(Game.LOL)
            assertEquals(listOf(), res)
        }
    }

    @Test
    fun `given a request of create entity return a list of pairs with Ids and the alias propagated`() {
        runBlocking {
            val alias = "kako"
            val alias2 = "sancho"
            val initialState = EntitiesState(
                listOf(),
                listOf(),
                listOf(basicLolEntity)
            )
            val entitiesService = createService(initialState)

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

            val insertRequest = LolEnrichedEntityRequest(name=basicLolEntity2.name, tag=basicLolEntity2.tag, puuid=basicLolEntity2.puuid, summonerIconId=basicLolEntity2.summonerIcon, summonerLevel=basicLolEntity2.summonerLevel)

            val expected = ResolvedEntities(
                listOf(insertRequest to alias2),
                listOf(basicLolEntity to alias),
                null
            )
            val result = entitiesService.resolveEntities(listOf(request, requestNotInState), Game.LOL)


            result
                .onLeft { fail() }
                .onRight { res ->
                    assertResolvedEntities(expected, res)
                    assertEquals(expected.entities.map { it.second }, res.entities.map { it.second })
                    assertEquals(expected.existing.map { it.second }, res.existing.map { it.second })
                }

        }
    }

    fun `insert`() {

    }

    private suspend fun createService(entitiesState: EntitiesState): EntitiesService {
        val entitiesRepository = EntitiesInMemoryRepository().withState(entitiesState)
        val wowGuildsRepository = WowGuildsInMemoryRepository()
        val viewsRepository = ViewsInMemoryRepository()

        val wowResolver = WowResolver(entitiesRepository, raiderIoClient)
        val wowHardcoreResolver = WowHardcoreResolver(entitiesRepository, blizzardClient)
        val lolResolver = LolResolver(entitiesRepository, riotClient)

        val entitiesResolver = mapOf(
            Game.WOW to wowResolver,
            Game.WOW_HC to wowHardcoreResolver,
            Game.LOL to lolResolver
        )

        val lolUpdater = LolUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)

        return EntitiesService(
            entitiesRepository,
            wowGuildsRepository,
            entitiesResolver,
            lolUpdater,
            wowHardcoreGuildUpdater
        )
    }

    private fun assertResolvedEntities(expected: ResolvedEntities, actual: ResolvedEntities) {
        println("expected: $expected")
        println("actual: $actual")
        assertEquals(expected.entities.size, actual.entities.size)
        assertEquals(expected.existing, actual.existing)
        assertEquals(expected.guild, actual.guild)
    }
}