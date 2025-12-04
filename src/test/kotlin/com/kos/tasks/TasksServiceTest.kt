package com.kos.tasks

import arrow.core.Either
import com.kos.auth.AuthService
import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.domain.ExpansionSeasons
import com.kos.clients.domain.QueueType
import com.kos.clients.domain.Season
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
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.seasons.SeasonService
import com.kos.seasons.repository.SeasonInMemoryRepository
import com.kos.staticdata.WowExpansion
import com.kos.staticdata.repository.StaticDataInMemoryRepository
import com.kos.staticdata.repository.StaticDataState
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
    private val blizzardDbClient = mock(BlizzardDatabaseClient::class.java)
    private val retryConfig = RetryConfig(1, 1)


    @Test
    fun `task update mythic plus dungeon season is successful`() = runBlocking {
        val tasksService = createTaskService()

        val expected = ExpansionSeasons(listOf(Season(true, "TWW3", 15, listOf())))
        tasksService.staticDataRepo.withState(StaticDataState(listOf(WowExpansion(10, "TWW", true))))

        `when`(raiderIoClient.getExpansionSeasons(10))
            .thenReturn(Either.Right(expected))

        val id = UUID.randomUUID().toString()

        tasksService.tasksService.runTask(TaskType.TASK_UPDATE_MYTHIC_PLUS_SEASON, id, emptyMap())

        val inserted = tasksService.tasksRepo.state().first()
        assertEquals(15, tasksService.seasonRepo.state().wowSeasons[0].id)
        assertEquals(id, inserted.id)
        assertEquals(Status.SUCCESSFUL, inserted.taskStatus.status)
    }

    @Test
    fun `token cleanup task should cleanup tokens`() = runBlocking {
        val testComponents = createTaskService()

        testComponents.authRepo.withState(
            listOf(
                basicAuthorization,
                basicAuthorization.copy(validUntil = OffsetDateTime.now().minusHours(1))
            )
        )

        val id = UUID.randomUUID().toString()

        testComponents.tasksService.tokenCleanup(id)

        val insertedTask = testComponents.tasksRepo.state().first()

        assertEquals(listOf(basicAuthorization), testComponents.authRepo.state())
        assertEquals(1, testComponents.tasksRepo.state().size)
        assertEquals(id, insertedTask.id)
        assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
        assertEquals(TaskType.TOKEN_CLEANUP_TASK, insertedTask.type)
    }

    @Test
    fun `tasks cleanup task should cleanup old tasks`() = runBlocking {
        val testComponents = createTaskService()

        val now = OffsetDateTime.now()
        val expectedRemainingTask = task(now)

        testComponents.tasksRepo.withState(
            listOf(
                expectedRemainingTask,
                task(now.minusDays(8))
            )
        )

        val id = UUID.randomUUID().toString()

        testComponents.tasksService.taskCleanup(id)

        val insertedTask = testComponents.tasksRepo.state().last()

        assertEquals(listOf(expectedRemainingTask, insertedTask), testComponents.tasksRepo.state())
        assertEquals(id, insertedTask.id)
        assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
        assertEquals(TaskType.TASK_CLEANUP_TASK, insertedTask.type)
    }

    @Test
    fun `data cache wow task should cache wow entities`() = runBlocking {
        val testComponents = createTaskService()

        testComponents.entitiesRepo.withState(
            EntitiesState(listOf(basicWowEntity), listOf(), listOf())
        )

        `when`(raiderIoClient.get(basicWowEntity))
            .thenReturn(RaiderIoMockHelper.get(basicWowEntity))
        `when`(raiderIoClient.cutoff())
            .thenReturn(RaiderIoMockHelper.cutoff())

        val id = UUID.randomUUID().toString()

        testComponents.tasksService.cacheDataTask(Game.WOW, TaskType.CACHE_WOW_DATA_TASK, id)

        val insertedTask = testComponents.tasksRepo.state().first()

        assertEquals(1, testComponents.dataCacheRepo.state().size)
        assertEquals(id, insertedTask.id)
        assertEquals(TaskType.CACHE_WOW_DATA_TASK, insertedTask.type)
    }

    @Test
    fun `data cache lol task should cache lol entities`() = runBlocking {
        val testComponents = createTaskService()

        testComponents.entitiesRepo.withState(
            EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
        )

        `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid))
            .thenReturn(RiotMockHelper.leagueEntries)
        `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.SOLO_Q.toInt()))
            .thenReturn(RiotMockHelper.matches)
        `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt()))
            .thenReturn(RiotMockHelper.matches)
        `when`(riotClient.getMatchById(RiotMockHelper.matchId))
            .thenReturn(Either.Right(RiotMockHelper.match))

        val id = UUID.randomUUID().toString()

        testComponents.tasksService.cacheDataTask(Game.LOL, TaskType.CACHE_LOL_DATA_TASK, id)

        val insertedTask = testComponents.tasksRepo.state().first()

        assertEquals(1, testComponents.dataCacheRepo.state().size)
        assertEquals(TaskType.CACHE_LOL_DATA_TASK, insertedTask.type)
    }

    @Test
    fun `update lol entities task should update lol entities correctly`() = runBlocking {
        val testComponents = createTaskService()

        `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid))
            .thenReturn(Either.Right(EntitiesTestHelper.basicGetSummonerResponse))
        `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid))
            .thenReturn(Either.Right(EntitiesTestHelper.basicGetAccountResponse))

        val id = UUID.randomUUID().toString()

        testComponents.tasksService.updateLolEntities(id)

        val insertedTask = testComponents.tasksRepo.state().first()

        assertEquals(1, testComponents.tasksRepo.state().size)
        assertEquals(TaskType.UPDATE_LOL_ENTITIES_TASK, insertedTask.type)
    }

    @Test
    fun `run task with correct parameters should run token cleanup task`() = runBlocking {
        val testComponents = createTaskService()
        val id = UUID.randomUUID().toString()

        testComponents.tasksService.runTask(TaskType.TOKEN_CLEANUP_TASK, id, emptyMap())

        val insertedTask = testComponents.tasksRepo.state().first()

        assertEquals(TaskType.TOKEN_CLEANUP_TASK, insertedTask.type)
    }


    @Test
    fun `run task with correct parameters should run wow data cache task`() = runBlocking {
        val testComponents = createTaskService()

        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())

        val id = UUID.randomUUID().toString()

        testComponents.tasksService.runTask(TaskType.CACHE_WOW_DATA_TASK, id, emptyMap())

        val insertedTask = testComponents.tasksRepo.state().first()

        assertEquals(TaskType.CACHE_WOW_DATA_TASK, insertedTask.type)
    }

    @Test
    fun `run task with correct parameters should run lol data cache task`() = runBlocking {
        val testComponents = createTaskService()
        val id = UUID.randomUUID().toString()

        testComponents.tasksService.runTask(TaskType.CACHE_LOL_DATA_TASK, id, emptyMap())

        val insertedTask = testComponents.tasksRepo.state().first()

        assertEquals(TaskType.CACHE_LOL_DATA_TASK, insertedTask.type)
    }


    @Test
    fun `I can get tasks`() = runBlocking {
        val testComponents = createTaskService()

        val now = OffsetDateTime.now()
        val expected = task(now)
        testComponents.tasksRepo.withState(listOf(expected))

        assertEquals(listOf(expected), testComponents.tasksService.getTasks(null))
    }


    @Test
    fun `I can get tasks by id`() = runBlocking {
        val testComponents = createTaskService()

        val now = OffsetDateTime.now()
        val knownId = "1"
        val expected = task(now).copy(id = knownId)

        testComponents.tasksRepo.withState(listOf(expected))

        assertEquals(expected, testComponents.tasksService.getTask(knownId))
    }


    private data class TaskServiceTestComponents(
        val dataCacheRepo: DataCacheInMemoryRepository,
        val entitiesRepo: EntitiesInMemoryRepository,
        val eventStore: EventStoreInMemory,

        val dataCacheService: DataCacheService,
        val entitiesService: EntitiesService,

        val credentialsRepo: CredentialsInMemoryRepository,
        val rolesRepo: RolesInMemoryRepository,
        val rolesActRepo: RolesActivitiesInMemoryRepository,
        val credentialsService: CredentialsService,
        val rolesService: RolesService,

        val authRepo: AuthInMemoryRepository,
        val authService: AuthService,

        val staticDataRepo: StaticDataInMemoryRepository,
        val seasonRepo: SeasonInMemoryRepository,
        val seasonService: SeasonService,

        val tasksRepo: TasksInMemoryRepository,
        val tasksService: TasksService
    )

    private fun createTaskService(): TaskServiceTestComponents {

        val dataCacheRepo = DataCacheInMemoryRepository()
        val entitiesRepository = EntitiesInMemoryRepository()
        val eventStore = EventStoreInMemory()

        val dataCacheService = DataCacheService(
            dataCacheRepo,
            entitiesRepository,
            raiderIoClient,
            riotClient,
            blizzardClient,
            blizzardDbClient,
            retryConfig,
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

        val credentialsRepo = CredentialsInMemoryRepository()
        val rolesRepo = RolesInMemoryRepository()
        val rolesActRepo = RolesActivitiesInMemoryRepository()

        val credentialsService = CredentialsService(credentialsRepo)
        val rolesService = RolesService(rolesRepo, rolesActRepo)

        val authRepo = AuthInMemoryRepository()
        val authService =
            AuthService(authRepo, credentialsService, rolesService, JWTConfig("issuer", "secret"))

        val staticDataRepo = StaticDataInMemoryRepository()
        val seasonRepo = SeasonInMemoryRepository()
        val seasonService = SeasonService(staticDataRepo, seasonRepo, raiderIoClient, retryConfig)

        val tasksRepo = TasksInMemoryRepository()
        val tasksService =
            TasksService(tasksRepo, dataCacheService, entitiesService, authService, seasonService)

        return TaskServiceTestComponents(
            dataCacheRepo,
            entitiesRepository,
            eventStore,

            dataCacheService,
            entitiesService,

            credentialsRepo,
            rolesRepo,
            rolesActRepo,
            credentialsService,
            rolesService,

            authRepo,
            authService,

            staticDataRepo,
            seasonRepo,
            seasonService,

            tasksRepo,
            tasksService
        )
    }


}