package com.kos.tasks

import arrow.core.Either
import com.kos.auth.AuthService
import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.domain.QueueType
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.JWTConfig
import com.kos.common.RetryConfig
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.datacache.DataCacheService
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.RiotMockHelper
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.entitiesResolvers.LolResolver
import com.kos.entities.entitiesResolvers.WowHardcoreResolver
import com.kos.entities.entitiesResolvers.WowResolver
import com.kos.entities.entitiesUpdaters.LolUpdater
import com.kos.entities.entitiesUpdaters.WowHardcoreGuildUpdater
import com.kos.entities.cache.EntityCacheServiceRegistry
import com.kos.entities.cache.LolEntityCacheService
import com.kos.entities.cache.WowEntityCacheService
import com.kos.entities.cache.WowHardcoreEntityCacheService
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TasksServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val blizzardDatabaseClient = mock(BlizzardDatabaseClient::class.java)
    private val retryConfig = RetryConfig(1, 1)

    @Test
    fun `token cleanup task should cleanup tokens`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository = EntitiesInMemoryRepository()
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )

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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)

            val authRepository = AuthInMemoryRepository().withState(
                listOf(
                    basicAuthorization,
                    basicAuthorization.copy(validUntil = OffsetDateTime.now().minusHours(1))
                )
            )
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            val id = UUID.randomUUID().toString()

            service.tokenCleanup(id)

            val insertedTask = tasksRepository.state().first()

            assertEquals(listOf(basicAuthorization), authRepository.state())
            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.TOKEN_CLEANUP_TASK, insertedTask.type)
        }
    }

    @Test
    fun `tasks cleanup task should cleanup old tasks`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository = EntitiesInMemoryRepository()
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )

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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )

            val now = OffsetDateTime.now()
            val expectedRemainingTask = task(now)
            val tasksRepository =
                TasksInMemoryRepository().withState(listOf(expectedRemainingTask, task(now.minusDays(8))))
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            val id = UUID.randomUUID().toString()

            service.taskCleanup(id)

            val insertedTask = tasksRepository.state().last()

            assertEquals(listOf(expectedRemainingTask, insertedTask), tasksRepository.state())
            assertEquals(2, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.TASK_CLEANUP_TASK, insertedTask.type)
        }
    }

    @Test
    fun `data cache wow task should cache wow entities`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(RaiderIoMockHelper.get(basicWowEntity))
            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())

            val id = UUID.randomUUID().toString()

            service.cacheDataTask(Game.WOW, TaskType.CACHE_WOW_DATA_TASK, id)

            val insertedTask = tasksRepository.state().first()

            assertEquals(1, dataCacheRepository.state().size)
            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.CACHE_WOW_DATA_TASK, insertedTask.type)
        }
    }

    @Test
    fun `data cache lol task should cache lol entities`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid)).thenReturn(RiotMockHelper.leagueEntries)
            `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.SOLO_Q.toInt())).thenReturn(
                RiotMockHelper.matches
            )
            `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt())).thenReturn(
                RiotMockHelper.matches
            )
            `when`(riotClient.getMatchById(RiotMockHelper.matchId)).thenReturn(Either.Right(RiotMockHelper.match))

            val id = UUID.randomUUID().toString()

            service.cacheDataTask(Game.LOL, TaskType.CACHE_LOL_DATA_TASK, id)

            val insertedTask = tasksRepository.state().first()

            assertEquals(1, dataCacheRepository.state().size)
            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.CACHE_LOL_DATA_TASK, insertedTask.type)
        }
    }

    @Test
    fun `update lol entities task should update lol entities correctly`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid)).thenReturn(Either.Right(EntitiesTestHelper.basicGetSummonerResponse))
            `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid)).thenReturn(Either.Right(EntitiesTestHelper.basicGetAccountResponse))

            val id = UUID.randomUUID().toString()

            service.updateLolEntities(id)

            val insertedTask = tasksRepository.state().first()

            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.UPDATE_LOL_ENTITIES_TASK, insertedTask.type)
        }
    }

    @Test
    fun `run task with correct parameters should run token cleanup task`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository = EntitiesInMemoryRepository()
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )

            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            val id = UUID.randomUUID().toString()

            service.runTask(TaskType.TOKEN_CLEANUP_TASK, id, mapOf())

            val insertedTask = tasksRepository.state().first()

            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.TOKEN_CLEANUP_TASK, insertedTask.type)
        }
    }

    @Test
    fun `run task with correct parameters should run wow data cache task`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository = EntitiesInMemoryRepository()
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())

            val id = UUID.randomUUID().toString()

            service.runTask(TaskType.CACHE_WOW_DATA_TASK, id, mapOf())

            val insertedTask = tasksRepository.state().first()

            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.CACHE_WOW_DATA_TASK, insertedTask.type)
        }
    }

    @Test
    fun `run task with correct parameters should run lol data cache task`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository = EntitiesInMemoryRepository()
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val credentialsService = CredentialsService(credentialsRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )

            val id = UUID.randomUUID().toString()

            service.runTask(TaskType.CACHE_LOL_DATA_TASK, id, mapOf())

            val insertedTask = tasksRepository.state().first()

            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.CACHE_LOL_DATA_TASK, insertedTask.type)
        }
    }

    @Test
    fun `I can get tasks`() {
        runBlocking {
            val now = OffsetDateTime.now()
            val task = task(now)

            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository = EntitiesInMemoryRepository()
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val credentialsService = CredentialsService(credentialsRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))


            val tasksRepository = TasksInMemoryRepository().withState(listOf(task))
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )
            assertEquals(listOf(task), service.getTasks(null))
        }
    }

    @Test
    fun `I can get tasks by id`() {
        runBlocking {
            val now = OffsetDateTime.now()
            val knownId = "1"
            val task = task(now).copy(id = knownId)

            val dataCacheRepository = DataCacheInMemoryRepository()
            val entitiesRepository = EntitiesInMemoryRepository()
            val eventStore = EventStoreInMemory()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
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

            val entitiesService = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val credentialsService = CredentialsService(credentialsRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository().withState(listOf(task))
            val entityCacheServiceRegistry = EntityCacheServiceRegistry(
                listOf(
                    LolEntityCacheService(dataCacheRepository, riotClient, retryConfig),
                    WowHardcoreEntityCacheService(
                        dataCacheRepository,
                        entitiesRepository,
                        raiderIoClient,
                        blizzardClient,
                        blizzardDatabaseClient,
                        retryConfig
                    ),
                    WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                )
            )
            val service =
                TasksService(
                    tasksRepository,
                    dataCacheService,
                    entitiesService,
                    authService,
                    entityCacheServiceRegistry
                )
            assertEquals(task, service.getTask(knownId))
        }
    }
}