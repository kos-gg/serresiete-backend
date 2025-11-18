package com.kos.views

import arrow.core.Either
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntity2
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity2
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.domain.GetPUUIDResponse
import com.kos.clients.domain.GetSummonerResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.RetryConfig
import com.kos.common.TooMuchEntities
import com.kos.common.TooMuchViews
import com.kos.common.UserWithoutRoles
import com.kos.credentials.Credentials
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper.basicCredentialsWithRolesInitialState
import com.kos.credentials.CredentialsTestHelper.emptyCredentialsInitialState
import com.kos.credentials.CredentialsTestHelper.password
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.datacache.DataCache
import com.kos.datacache.DataCacheService
import com.kos.datacache.RiotMockHelper.anotherRiotData
import com.kos.datacache.TestHelper.anotherLolDataCache
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.*
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.roles.Role
import com.kos.views.ViewsTestHelper.basicSimpleGameViews
import com.kos.views.ViewsTestHelper.basicSimpleLolView
import com.kos.views.ViewsTestHelper.basicSimpleLolViews
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.ViewsTestHelper.id
import com.kos.views.ViewsTestHelper.name
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.ViewsTestHelper.published
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import io.mockk.InternalPlatformDsl.toStr
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

//TODO: Add testing for:
//TODO: getCachedData(simpleView: SimpleView)
//TODO: getData when view is from WOW
//TODO: getData when view is from LOL

class ViewsServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val blizzardDatabaseClient = mock(BlizzardDatabaseClient::class.java)
    private val retryConfig = RetryConfig(1, 1)

    private val aggregateRoot = "/credentials/owner"
    private val defaultCredentialsState = CredentialsRepositoryState(
        listOf(Credentials(owner, password)),
        mapOf(owner to listOf(Role.USER))
    )

    @Nested
    inner class BehaviorOfGetViews {
        @Test
        fun `i can get own views`() {
            runBlocking {
                val (_, viewsService) = createService(
                    ViewsState(listOf(basicSimpleWowView), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    emptyCredentialsInitialState
                )

                assertEquals(listOf(basicSimpleWowView), viewsService.getOwnViews(owner))
            }
        }

        @Test
        fun `i can get views returns only one view since the limit is 1`() {
            runBlocking {
                val limit = 1

                val (_, viewsService) = createService(
                    ViewsState(basicSimpleGameViews, listOf()),
                    emptyEntitiesState,
                    listOf(),
                    emptyCredentialsInitialState
                )
                assertEquals(
                    limit,
                    viewsService.getViews(Game.WOW, false, null, limit).second.size
                )
            }
        }

        @Test
        fun `i can get views returns empty since the page and limit goes beyond actual rows in database`() {
            runBlocking {
                val page = 2
                val limit = 10

                val (_, viewsService) = createService(
                    ViewsState(basicSimpleGameViews, listOf()),
                    emptyEntitiesState,
                    listOf(),
                    emptyCredentialsInitialState
                )
                assertEquals(
                    0,
                    viewsService.getViews(Game.WOW, false, page, limit).second.size
                )
            }
        }

        @Test
        fun `i can get a simple view`() {
            runBlocking {
                val (_, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    emptyCredentialsInitialState
                )

                assertEquals(basicSimpleWowView, viewsService.getSimple("1"))
            }
        }

        @Test
        fun `i can get views of a game`() {
            runBlocking {
                val (_, viewsService) = createService(
                    ViewsState(basicSimpleGameViews, listOf()),
                    emptyEntitiesState,
                    listOf(),
                    emptyCredentialsInitialState
                )

                assertEquals(basicSimpleLolViews, viewsService.getViews(Game.LOL, false, null, null).second)
            }
        }

        @Test
        fun `i can get views returns only wow featured views`() {
            runBlocking {
                val (_, viewsService) = createService(
                    ViewsState(basicSimpleGameViews, listOf()),
                    emptyEntitiesState,
                    listOf(),
                    emptyCredentialsInitialState
                )

                assertEquals(
                    listOf(basicSimpleWowView.copy(id = "3", featured = true)),
                    viewsService.getViews(Game.WOW, true, null, null).second
                )
            }
        }

        @Test
        fun `i can get view with alias of entities`() {
            runBlocking {
                val lolEntities = listOf(basicLolEntity, basicLolEntity2)
                val alias = "kako"
                val viewEntityOne = ViewEntity(basicLolEntity.id, basicSimpleLolView.id, alias)
                val viewEntityTwo = ViewEntity(basicLolEntity2.id, basicSimpleLolView.id, null)
                val (_, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = lolEntities.map { it.id })),
                        listOf(viewEntityOne, viewEntityTwo)
                    ),
                    EntitiesState(listOf(), listOf(), lolEntities),
                    listOf(),
                    defaultCredentialsState,
                )
                val view = viewsService.get(basicSimpleLolView.id)

                assertEquals(listOf(alias, null), view?.entities?.map { it.alias })
            }
        }
    }

    @Nested
    inner class BehaviorOfCreateView {

        @Test
        fun `create a wow view stores a create view event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState,
                )

                viewsService.create(
                    owner,
                    ViewRequest(name, published, listOf(), Game.WOW, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_CREATED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    aggregateRoot,
                    name,
                    published,
                    listOf(),
                    Game.WOW, false
                )
            }
        }

        @Test
        fun `create a lol view stores a create view event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState,
                )

                viewsService.create(
                    owner,
                    ViewRequest(name, published, listOf(), Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_CREATED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    aggregateRoot,
                    name,
                    published,
                    listOf(),
                    Game.LOL,
                    false
                )
            }
        }

        @Test
        fun `create a lol view with some characters stores a create view event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState
                )

                val charactersRequest = (1..10).map { LolEntityRequest(it.toString(), it.toString()) }

                viewsService.create(
                    owner,
                    ViewRequest(name, published, charactersRequest, Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_CREATED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    aggregateRoot,
                    name,
                    published,
                    charactersRequest,
                    Game.LOL, false
                )

            }
        }

        @Test
        fun `trying to exceed the maximum number of views allowed does not store an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(listOf(basicSimpleLolView, basicSimpleLolView), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState
                )

                viewsService.create(
                    owner,
                    ViewRequest(name, published, listOf(), Game.WOW, false)
                ).onRight {
                    fail()
                }.onLeft {
                    assertTrue(it is TooMuchViews)
                }

                assertNoEventsStored(eventStore)
            }
        }

        @Test
        fun `create a lol view with too many characters does not store an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState
                )

                val charactersRequest = (1..11).map { LolEntityRequest(it.toString(), it.toString()) }

                viewsService.create(owner, ViewRequest(name, published, charactersRequest, Game.WOW, false)).onRight {
                    fail()
                }.onLeft {
                    assertTrue(it is TooMuchEntities)
                }

                assertNoEventsStored(eventStore)
            }
        }

        @Test
        fun `admins can create a huge amount of views and an event gets stored`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState((1..100).map {
                        SimpleView(
                            it.toStr(),
                            it.toStr(),
                            owner,
                            true,
                            listOf(),
                            Game.WOW,
                            false
                        )
                    }, listOf()),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(
                        listOf(Credentials(owner, password)),
                        mapOf(owner to listOf(Role.ADMIN))
                    )
                )

                viewsService.create(
                    owner,
                    ViewRequest(name, published, listOf(), Game.WOW, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_CREATED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    aggregateRoot,
                    name,
                    published,
                    listOf(),
                    Game.WOW, false
                )
            }
        }

        @Test
        fun `user without role trying to create a view does not store an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(listOf(Credentials(owner, password)), mapOf(owner to listOf()))
                )

                viewsService.create(
                    owner,
                    ViewRequest(name, published, listOf(), Game.WOW, false)
                ).onRight {
                    fail()
                }.onLeft {
                    assertTrue(it is UserWithoutRoles)
                }

                assertNoEventsStored(eventStore)
            }
        }

        @Test
        fun `create view processing view to be created event stores an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState
                )

                createViewFromEventAndAssert(
                    viewsService,
                    ViewToBeCreatedEvent(id, name, published, listOf(), Game.LOL, owner, false, null)
                )

                assertEventStoredCorrectly(
                    eventStore,
                    ViewCreatedEvent(id, name, owner, listOf(), published, Game.LOL, false, null)
                )
            }
        }

        private suspend fun createViewFromEventAndAssert(
            viewsService: ViewsService,
            viewToBeCreatedEvent: ViewToBeCreatedEvent
        ) {
            viewsService.createView(
                id,
                aggregateRoot,
                viewToBeCreatedEvent
            ).onRight {
                assertOperation(it, EventType.VIEW_CREATED)
            }.onLeft {
                fail(it.message)
            }
        }
    }

    @Nested
    inner class BehaviorOfEditView {

        @Test
        fun `editing a lol view stores an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState
                )

                val newName = "new-name"
                viewsService.edit(
                    owner,
                    id,
                    ViewRequest(newName, published, listOf(), Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewToBeEditedEvent(id, newName, published, listOf(), Game.LOL, false)
                )
            }
        }

        @Test
        fun `editing a lol view with too many characters does not store an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(listOf(Credentials(owner, password)), mapOf(owner to listOf(Role.USER)))
                )
                val charactersRequest = (1..11).map { LolEntityRequest(it.toString(), it.toString()) }

                viewsService.edit(
                    owner, id,
                    ViewRequest(name, published, charactersRequest, Game.LOL, false)
                ).onRight {
                    fail()
                }.onLeft {
                    assertTrue(it is TooMuchEntities)
                }

                assertNoEventsStored(eventStore)
            }
        }

        @Test
        fun `editing a wow view with more than one character stores an event`() {
            runBlocking {

                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState
                )

                val charactersRequest = listOf(request1, request2, request3, request4)
                viewsService.edit(
                    owner,
                    id,
                    ViewRequest(name, published, charactersRequest, Game.WOW, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewToBeEditedEvent(id, name, published, charactersRequest, Game.WOW, false)
                )

            }
        }

        @Test
        fun `editing a lol view processing view to be edited stores an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    defaultCredentialsState
                )

                val newName = "new-name"
                viewsService.editView(
                    id,
                    aggregateRoot,
                    ViewToBeEditedEvent(id, newName, published, listOf(), Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewEditedEvent(id, newName, listOf(), published, Game.LOL, false)
                )
            }
        }

        @Test
        fun `editing a view processing view to be edited, an event is stored with the actual characters of the view`() {
            runBlocking {
                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                `when`(raiderIoClient.exists(request1)).thenReturn(true)
                `when`(raiderIoClient.exists(request2)).thenReturn(true)
                `when`(raiderIoClient.exists(request3)).thenReturn(true)
                `when`(raiderIoClient.exists(request4)).thenReturn(true)

                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    EntitiesState(
                        listOf(basicWowEntity, basicWowEntity2),
                        listOf(),
                        listOf()
                    ),
                    listOf(),
                    basicCredentialsWithRolesInitialState
                )

                viewsService.editView(
                    id,
                    aggregateRoot,
                    ViewToBeEditedEvent(
                        id,
                        name,
                        published,
                        listOf(request1, request2, request3, request4),
                        Game.WOW,
                        false
                    )
                ).onRight {
                    assertOperation(it, EventType.VIEW_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewEditedEvent(id, name, listOf(3, 4, 5, 6), published, Game.WOW, false)
                )
            }
        }

        @Test
        fun `editing a lol view processing view to be edited, an event is stored with the actual characters of the view`() {
            runBlocking {
                val charactersRequest = (3..6).map { LolEntityRequest(it.toString(), it.toString()) }

                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = listOf(1))),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    EntitiesState(
                        listOf(),
                        listOf(),
                        listOf(basicLolEntity, basicLolEntity2)
                    ),
                    listOf(),
                    basicCredentialsWithRolesInitialState
                )

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

                viewsService.editView(
                    id,
                    aggregateRoot,
                    ViewToBeEditedEvent(id, name, published, charactersRequest, Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewEditedEvent(id, name, listOf(3, 4, 5, 6), published, Game.LOL, false)
                )
            }
        }
    }

    @Nested
    inner class BehaviorOfDeleteView {
        @Test
        fun `i can delete a view`() {
            runBlocking {

                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    emptyCredentialsInitialState
                )

                val result = viewsService.delete(owner, basicSimpleWowView)
                assertOperation(result, EventType.VIEW_DELETED)
                assertEventStoredCorrectly(
                    eventStore,
                    ViewDeletedEvent(
                        basicSimpleWowView.id,
                        basicSimpleWowView.name,
                        basicSimpleWowView.owner,
                        basicSimpleWowView.entitiesIds,
                        basicSimpleWowView.published,
                        basicSimpleWowView.game,
                        basicSimpleWowView.featured
                    )
                )
            }
        }
    }

    @Nested
    inner class BehaviorOfPatchView {
        @Test
        fun `patch a view stores an event`() {
            runBlocking {
                val patchedName = "new-name"

                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(listOf(Credentials(owner, password)), mapOf(owner to listOf(Role.USER)))
                )

                viewsService.patch(
                    owner,
                    id,
                    ViewPatchRequest(patchedName, null, null, Game.WOW, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_PATCHED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewToBePatchedEvent(id, patchedName, null, null, Game.WOW, false)
                )
            }
        }

        @Test
        fun `trying to patch a view with too many characters fails without storing an event`() {
            runBlocking {
                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(listOf(Credentials(owner, password)), mapOf(owner to listOf(Role.USER)))
                )

                val id = UUID.randomUUID().toString()

                val charactersRequest = (1..11).map { LolEntityRequest(it.toString(), it.toString()) }

                viewsService.patch(
                    owner,
                    id,
                    ViewPatchRequest(null, null, charactersRequest, Game.WOW, false)
                ).onRight {
                    fail()
                }.onLeft {
                    assertTrue(it is TooMuchEntities)
                }

                assertNoEventsStored(eventStore)
            }
        }

        @Test
        fun `patching a view patching more than one character stores an event`() {
            runBlocking {

                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = listOf(1))),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(listOf(Credentials(owner, password)), mapOf(owner to listOf(Role.USER)))
                )

                val characterRequests = listOf(request1, request2, request3, request4)
                viewsService.patch(
                    owner,
                    id,
                    ViewPatchRequest(null, null, characterRequests, Game.WOW, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_TO_BE_PATCHED)
                }.onLeft {
                    fail(it.toStr())
                }

                val expectedEvent = Event(
                    aggregateRoot,
                    id,
                    ViewToBePatchedEvent(id, null, null, characterRequests, Game.WOW, false)
                )

                val events = eventStore.getEvents(null).toList()

                assertEquals(1, events.size)
                assertEquals(EventWithVersion(1, expectedEvent), events.first())

            }
        }

        @Test
        fun `patching a lol view processing event stores an event`() {
            runBlocking {
                val charactersRequest = (3..6).map { LolEntityRequest(it.toString(), it.toString()) }

                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = listOf(1))),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(listOf(Credentials(owner, password)), mapOf(owner to listOf(Role.USER)))
                )

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

                viewsService.patchView(
                    id,
                    aggregateRoot,
                    ViewToBePatchedEvent(id, null, null, charactersRequest, Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_PATCHED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewPatchedEvent(id, null, listOf(1, 2, 3, 4), null, Game.LOL, false)
                )
            }
        }

        @Test
        fun `patching a wow view processing event stores an event`() {
            runBlocking {

                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                `when`(raiderIoClient.exists(request1)).thenReturn(true)
                `when`(raiderIoClient.exists(request2)).thenReturn(true)
                `when`(raiderIoClient.exists(request3)).thenReturn(true)
                `when`(raiderIoClient.exists(request4)).thenReturn(true)


                val (eventStore, viewsService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState,
                    listOf(),
                    CredentialsRepositoryState(listOf(Credentials(owner, password)), mapOf(owner to listOf(Role.USER)))
                )

                val charactersRequest = listOf(request1, request2, request3, request4)

                viewsService.patchView(
                    id,
                    aggregateRoot,
                    ViewToBePatchedEvent(id, null, null, charactersRequest, Game.WOW, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_PATCHED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewPatchedEvent(id, null, listOf(1, 2, 3, 4), null, Game.WOW, false)
                )
            }
        }
    }

    @Nested
    inner class BehaviorOfGetData {
        @Test
        fun `lol view data returns newest cached data`() {
            runBlocking {

                val simpleView = basicSimpleLolView.copy(entitiesIds = listOf(1))
                val view = View(
                    simpleView.id, simpleView.name, simpleView.owner, simpleView.published, listOf(
                        EntityWithAlias(basicLolEntity, null)
                    ), simpleView.game, simpleView.featured
                )

                val moreRecentDataCache =
                    anotherLolDataCache.copy(entityId = 1, inserted = OffsetDateTime.now().plusHours(2))

                val (_, viewsService) = createService(
                    ViewsState(
                        listOf(simpleView),
                        simpleView.entitiesIds.map { ViewEntity(it, simpleView.id, "alias") }),
                    EntitiesState(listOf(), listOf(), listOf(basicLolEntity)),
                    listOf(
                        lolDataCache.copy(entityId = 1),
                        moreRecentDataCache
                    ),
                    emptyCredentialsInitialState
                )

                viewsService.getData(view)
                    .onLeft { fail(it.error()) }
                    .onRight { assertEquals(listOf(anotherRiotData), it) }
            }
        }
    }

    private suspend fun createService(
        viewsState: ViewsState,
        entitiesState: EntitiesState,
        dataCacheState: List<DataCache>,
        credentialState: CredentialsRepositoryState,
    ): Pair<EventStore, ViewsService> {
        val viewsRepository = ViewsInMemoryRepository()
            .withState(viewsState)
        val charactersRepository = EntitiesInMemoryRepository()
            .withState(entitiesState)
        val dataCacheRepository = DataCacheInMemoryRepository()
            .withState(dataCacheState)
        val credentialsRepository = CredentialsInMemoryRepository()
            .withState(credentialState)
        val eventStore = EventStoreInMemory()

        val credentialsService = CredentialsService(credentialsRepository)
        val entitiesService = EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)
        val dataCacheService =
            DataCacheService(
                dataCacheRepository,
                charactersRepository,
                raiderIoClient,
                riotClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig,
                eventStore
            )
        val service =
            ViewsService(
                viewsRepository,
                entitiesService,
                dataCacheService,
                credentialsService,
                eventStore
            )

        return Pair(eventStore, service)
    }

    private fun assertOperation(operation: Operation, expectedType: EventType) {
        assertTrue(operation.id.isNotEmpty())
        assertEquals(aggregateRoot, operation.aggregateRoot)
        assertEquals(expectedType, operation.type)
    }

    private suspend fun assertEventStoredCorrectly(
        eventStore: EventStore,
        aggregateRoot: String,
        viewName: String,
        published: Boolean,
        characters: List<CreateEntityRequest>,
        game: Game,
        featured: Boolean,
    ) {
        val events = eventStore.getEvents(null).toList()
        assertEquals(1, events.size)
        val actual = events.first().event
        val data = actual.eventData as ViewToBeCreatedEvent

        assertTrue(actual.operationId.isNotEmpty())
        assertEquals(aggregateRoot, actual.aggregateRoot)
        assertEquals(data.id, actual.operationId)
        assertEquals(viewName, data.name)
        assertEquals(published, data.published)
        assertEquals(characters, data.entities)
        assertEquals(game, data.game)
        assertEquals(owner, data.owner)
        assertEquals(featured, data.featured)
    }

    private suspend fun assertEventStoredCorrectly(eventStore: EventStore, eventData: EventData) {
        val events = eventStore.getEvents(null).toList()

        val expectedEvent = Event(
            aggregateRoot,
            id,
            eventData
        )

        assertEquals(1, events.size)
        assertEquals(EventWithVersion(1, expectedEvent), events.first())
    }

    private suspend fun assertNoEventsStored(eventStore: EventStore) {
        val events = eventStore.getEvents(null).toList()
        assertEquals(0, events.size)
    }
}