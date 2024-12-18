package com.kos.views

import com.kos.activities.Activities
import com.kos.assertTrue
import com.kos.characters.CharactersService
import com.kos.characters.CharactersTestHelper.basicLolCharacter
import com.kos.characters.CharactersTestHelper.basicWowCharacter
import com.kos.characters.CharactersTestHelper.basicWowRequest2
import com.kos.characters.CharactersTestHelper.emptyCharactersState
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersState
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.*
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper.basicCredentials
import com.kos.credentials.CredentialsTestHelper.emptyCredentialsState
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.datacache.DataCache
import com.kos.datacache.DataCacheService
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.RaiderIoMockHelper.raiderIoData
import com.kos.datacache.RaiderIoMockHelper.raiderIoDataString
import com.kos.datacache.RaiderIoMockHelper.raiderioCachedData
import com.kos.datacache.RiotMockHelper.riotData
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.roles.Role
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.views.ViewsTestHelper.basicSimpleLolView
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.repository.ViewsInMemoryRepository
import io.mockk.InternalPlatformDsl.toStr
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import kotlin.test.*

//TODO: Behaviour of get cached data

class ViewsControllerTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val retryConfig = RetryConfig(1, 1)
    private val viewsRepository = ViewsInMemoryRepository()
    private val charactersRepository = CharactersInMemoryRepository()
    private val dataCacheRepository = DataCacheInMemoryRepository()
    private val credentialsRepository = CredentialsInMemoryRepository()
    private val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
    private val eventStore = EventStoreInMemory()

    private suspend fun createController(
        credentialsState: CredentialsRepositoryState,
        viewsState: List<SimpleView>,
        charactersState: CharactersState,
        dataCacheState: List<DataCache>,
    ): ViewsController {
        val viewsRepositoryWithState = viewsRepository.withState(viewsState)
        val charactersRepositoryWithState = charactersRepository.withState(charactersState)
        val dataCacheRepositoryWithState = dataCacheRepository.withState(dataCacheState)
        val credentialsRepositoryWithState = credentialsRepository.withState(credentialsState)

        val dataCacheService =
            DataCacheService(dataCacheRepositoryWithState, raiderIoClient, riotClient, blizzardClient, retryConfig)
        val charactersService =
            CharactersService(charactersRepositoryWithState, raiderIoClient, riotClient, blizzardClient)
        val credentialsService = CredentialsService(credentialsRepositoryWithState)
        val viewsService = ViewsService(
            viewsRepositoryWithState,
            charactersService,
            dataCacheService,
            credentialsService,
            eventStore
        )

        return ViewsController(viewsService)
    }


    @BeforeTest
    fun beforeEach() {
        viewsRepository.clear()
        charactersRepository.clear()
        dataCacheRepository.clear()
        credentialsRepository.clear()
        rolesActivitiesRepository.clear()
    }

    @Test
    fun `i can get views returns only wow featured views`() {
        runBlocking {
            val featuredView = basicSimpleWowView.copy(featured = true)

            val controller = createController(
                emptyCredentialsState,
                listOf(basicSimpleWowView, featuredView),
                emptyCharactersState,
                listOf()
            )
            assertEquals(
                listOf(featuredView),
                controller.getViews("owner", setOf(Activities.getAnyViews), Game.WOW, true).getOrNull()
            )
        }
    }

    @Test
    fun `i can get views returns only owner views`() {
        runBlocking {
            val controller = createController(
                emptyCredentialsState,
                listOf(basicSimpleWowView, basicSimpleWowView.copy(owner = "not-owner")),
                emptyCharactersState,
                listOf()
            )
            assertEquals(
                listOf(basicSimpleWowView),
                controller.getViews("owner", setOf(Activities.getOwnViews), null, false).getOrNull()
            )
        }
    }

    @Test
    fun `i can get views returns all views if perms are given`() {
        runBlocking {
            val notOwnerView = basicSimpleWowView.copy(owner = "not-owner")
            val controller = createController(
                emptyCredentialsState,
                listOf(basicSimpleWowView, notOwnerView),
                emptyCharactersState,
                listOf()
            )
            assertEquals(
                listOf(basicSimpleWowView, notOwnerView),
                controller.getViews("owner", setOf(Activities.getAnyViews), null, false).getOrNull()
            )
        }
    }

    @Test
    fun `i can get view returns view only if i own it`() {
        runBlocking {
            val notOwnerView = basicSimpleWowView.copy(owner = "not-owner", id = "2")
            val controller = createController(
                emptyCredentialsState,
                listOf(basicSimpleWowView, notOwnerView),
                emptyCharactersState,
                listOf()
            )
            assertEquals(
                basicSimpleWowView,
                controller.getView("owner", basicSimpleWowView.id, setOf(Activities.getOwnView)).getOrNull()?.toSimple()
            )
            assertEquals(
                NotEnoughPermissions("owner"),
                controller.getView("owner", notOwnerView.id, setOf(Activities.getOwnView)).getLeftOrNull()
            )
        }
    }

    @Test
    fun `i can't get view that does not exist`() {
        runBlocking {
            val controller = createController(
                emptyCredentialsState,
                listOf(),
                emptyCharactersState,
                listOf()
            )
            assertEquals(
                NotFound(basicSimpleWowView.id),
                controller.getView("owner", basicSimpleWowView.id, setOf(Activities.getOwnView)).getLeftOrNull()
            )
        }
    }

    @Test
    fun `i can create views`() {
        runBlocking {

            val controller = createController(
                CredentialsRepositoryState(
                    listOf(basicCredentials.copy(userName = "owner")),
                    mapOf(owner to listOf(Role.USER))
                ),
                listOf(),
                emptyCharactersState,
                listOf()
            )
            val res =
                controller.createView(
                    "owner",
                    ViewRequest(basicSimpleWowView.name, true, listOf(), Game.WOW, false),
                    setOf(Activities.createViews)
                )
                    .getOrNull()

            assertTrue(res?.id?.isNotEmpty())
            assertEquals("/credentials/owner", res?.aggregateRoot)
            assertEquals(EventType.VIEW_TO_BE_CREATED, res?.type)
        }
    }

    @Test
    fun `i can't create too much views`() {
        runBlocking {
            val controller = createController(
                CredentialsRepositoryState(
                    listOf(basicCredentials.copy(userName = "owner")),
                    mapOf(owner to listOf(Role.USER))
                ),
                listOf(basicSimpleWowView, basicSimpleWowView),
                emptyCharactersState,
                listOf()
            )

            assertIs<TooMuchViews>(
                controller.createView(
                    "owner",
                    ViewRequest(basicSimpleWowView.name, true, listOf(), Game.WOW, false),
                    setOf(Activities.createViews)
                ).getLeftOrNull()
            )
        }
    }

    @Test
    fun `i can get wow view data`() {
        runBlocking {
            val controller = createController(
                emptyCredentialsState,
                listOf(basicSimpleWowView.copy(characterIds = listOf(1))),
                CharactersState(listOf(basicWowCharacter), listOf(), listOf()),
                listOf(DataCache(basicWowCharacter.id, raiderIoDataString, OffsetDateTime.now(), Game.WOW))
            )

            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowCharacter)).thenReturn(RaiderIoMockHelper.get(basicWowCharacter))

            controller.getViewData("owner", basicSimpleWowView.id, setOf(Activities.getViewData))
                .onRight {
                    assertEquals(ViewData(basicSimpleWowView.name, raiderIoData), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can get lol view data`() {
        runBlocking {
            val controller = createController(
                emptyCredentialsState,
                listOf(basicSimpleLolView.copy(characterIds = listOf(2))),
                CharactersState(listOf(), listOf(), listOf(basicLolCharacter.copy(id = 2))),
                listOf(lolDataCache)
            )

            controller.getViewData("owner", basicSimpleLolView.id, setOf(Activities.getViewData))
                .onRight {
                    assertEquals(ViewData(basicSimpleLolView.name, listOf(riotData)), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can get wow cached data`() {
        runBlocking {

            val controller = createController(
                emptyCredentialsState,
                listOf(basicSimpleWowView.copy(characterIds = listOf(1))),
                CharactersState(listOf(basicWowCharacter), listOf(), listOf()),
                listOf(wowDataCache)
            )

            controller.getViewCachedData("owner", basicSimpleWowView.id, setOf(Activities.getViewCachedData))
                .onRight {
                    assertEquals(ViewData(basicSimpleWowView.name, listOf(raiderioCachedData)), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can get lol cached data`() {
        runBlocking {
            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = "owner")),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                listOf(basicSimpleLolView.copy(characterIds = listOf(2))),
                CharactersState(listOf(), listOf(), listOf(basicLolCharacter)),
                listOf(lolDataCache)
            )

            controller.getViewCachedData("owner", basicSimpleLolView.id, setOf(Activities.getViewCachedData))
                .onRight {
                    assertEquals(ViewData(basicSimpleLolView.name, listOf(riotData)), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can edit wow data`() {
        runBlocking {
            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = "owner")),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                listOf(basicSimpleWowView),
                CharactersState(listOf(basicWowCharacter), listOf(), listOf(basicLolCharacter)),
                listOf(lolDataCache)
            )

            `when`(raiderIoClient.exists(basicWowRequest2)).thenReturn(true)

            val viewRequest = ViewRequest("new-name", false, characters = listOf(basicWowRequest2), Game.WOW, false)

            controller.editView("owner", viewRequest, basicSimpleWowView.id, setOf(Activities.editAnyView))
                .onRight {
                    assertTrue(it.id.isNotEmpty())
                    assertEquals("/credentials/owner", it.aggregateRoot)
                    assertEquals(EventType.VIEW_TO_BE_EDITED, it.type)
                }
                .onLeft { fail(it.toStr()) }
        }
    }
}