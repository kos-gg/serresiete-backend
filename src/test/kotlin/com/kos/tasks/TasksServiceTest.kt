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
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.seasons.SeasonService
import com.kos.seasons.repository.SeasonInMemoryRepository
import com.kos.staticdata.repository.StaticDataInMemoryRepository
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.junit.Before
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

    private lateinit var dataCacheRepository: DataCacheInMemoryRepository
    private lateinit var entitiesRepository: EntitiesInMemoryRepository
    private lateinit var eventStore: EventStoreInMemory

    private lateinit var dataCacheService: DataCacheService
    private lateinit var entitiesService: EntitiesService

    private lateinit var credentialsRepository: CredentialsInMemoryRepository
    private lateinit var rolesRepository: RolesInMemoryRepository
    private lateinit var rolesActivitiesRepository: RolesActivitiesInMemoryRepository
    private lateinit var credentialsService: CredentialsService
    private lateinit var rolesService: RolesService

    private lateinit var authRepository: AuthInMemoryRepository
    private lateinit var authService: AuthService

    private lateinit var seasonService: SeasonService

    @Before
    fun setup() {
        dataCacheRepository = DataCacheInMemoryRepository()
        entitiesRepository = EntitiesInMemoryRepository()
        eventStore = EventStoreInMemory()

        dataCacheService = DataCacheService(
            dataCacheRepository,
            entitiesRepository,
            raiderIoClient,
            riotClient,
            blizzardClient,
            blizzardDatabaseClient,
            retryConfig,
            eventStore
        )

        entitiesService = EntitiesService(
            entitiesRepository,
            raiderIoClient,
            riotClient,
            blizzardClient
        )

        credentialsRepository = CredentialsInMemoryRepository()
        rolesRepository = RolesInMemoryRepository()
        rolesActivitiesRepository = RolesActivitiesInMemoryRepository()

        credentialsService = CredentialsService(credentialsRepository)
        rolesService = RolesService(rolesRepository, rolesActivitiesRepository)

        authRepository = AuthInMemoryRepository()
        authService = AuthService(
            authRepository,
            credentialsService,
            rolesService,
            JWTConfig("issuer", "secret")
        )

        seasonService =
            SeasonService(StaticDataInMemoryRepository(), SeasonInMemoryRepository(), raiderIoClient, retryConfig)
    }

    @Test
    fun `task update mythic plus dungeon season is successful`() {

    }

    @Test
    fun `task update mythic plus dungeon season failed and is handled`() {

    }

    @Test
    fun `token cleanup task should cleanup tokens`() = runBlocking {
        authRepository.withState(
            listOf(
                basicAuthorization,
                basicAuthorization.copy(validUntil = OffsetDateTime.now().minusHours(1))
            )
        )

        val tasksRepository = TasksInMemoryRepository()
        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)

        val id = UUID.randomUUID().toString()
        service.tokenCleanup(id)

        val insertedTask = tasksRepository.state().first()

        assertEquals(listOf(basicAuthorization), authRepository.state())
        assertEquals(id, insertedTask.id)
        assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
        assertEquals(TaskType.TOKEN_CLEANUP_TASK, insertedTask.type)
    }

    @Test
    fun `tasks cleanup task should cleanup old tasks`() = runBlocking {
        val now = OffsetDateTime.now()
        val remaining = task(now)
        val tasksRepository = TasksInMemoryRepository()
            .withState(listOf(remaining, task(now.minusDays(8))))

        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)

        val id = UUID.randomUUID().toString()
        service.taskCleanup(id)

        val insertedTask = tasksRepository.state().last()

        assertEquals(listOf(remaining, insertedTask), tasksRepository.state())
        assertEquals(TaskType.TASK_CLEANUP_TASK, insertedTask.type)
    }

    @Test
    fun `data cache wow task should cache wow entities`() = runBlocking {
        entitiesRepository.withState(
            EntitiesState(listOf(basicWowEntity), listOf(), listOf())
        )

        val tasksRepository = TasksInMemoryRepository()
        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)


        `when`(raiderIoClient.get(basicWowEntity)).thenReturn(RaiderIoMockHelper.get(basicWowEntity))
        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())

        val id = UUID.randomUUID().toString()

        service.cacheDataTask(Game.WOW, TaskType.CACHE_WOW_DATA_TASK, id)

        val insertedTask = tasksRepository.state().first()
        assertEquals(1, dataCacheRepository.state().size)
        assertEquals(TaskType.CACHE_WOW_DATA_TASK, insertedTask.type)
    }

    @Test
    fun `data cache lol task should cache lol entities`() = runBlocking {
        entitiesRepository.withState(
            EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
        )

        val tasksRepository = TasksInMemoryRepository()
        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)


        `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid)).thenReturn(RiotMockHelper.leagueEntries)
        `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.SOLO_Q.toInt()))
            .thenReturn(RiotMockHelper.matches)
        `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt()))
            .thenReturn(RiotMockHelper.matches)
        `when`(riotClient.getMatchById(RiotMockHelper.matchId))
            .thenReturn(Either.Right(RiotMockHelper.match))

        val id = UUID.randomUUID().toString()
        service.cacheDataTask(Game.LOL, TaskType.CACHE_LOL_DATA_TASK, id)

        assertEquals(1, dataCacheRepository.state().size)
    }

    @Test
    fun `update lol entities task should update lol entities correctly`() = runBlocking {
        entitiesRepository.withState(
            EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
        )

        val tasksRepository = TasksInMemoryRepository()
        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)


        `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid))
            .thenReturn(Either.Right(EntitiesTestHelper.basicGetSummonerResponse))
        `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid))
            .thenReturn(Either.Right(EntitiesTestHelper.basicGetAccountResponse))

        val id = UUID.randomUUID().toString()
        service.updateLolEntities(id)

        val insertedTask = tasksRepository.state().first()
        assertEquals(TaskType.UPDATE_LOL_ENTITIES_TASK, insertedTask.type)
    }

    @Test
    fun `run task with correct parameters should run token cleanup task`() = runBlocking {
        val tasksRepository = TasksInMemoryRepository()
        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)


        val id = UUID.randomUUID().toString()
        service.runTask(TaskType.TOKEN_CLEANUP_TASK, id, emptyMap())

        assertEquals(TaskType.TOKEN_CLEANUP_TASK, tasksRepository.state().first().type)
    }

    @Test
    fun `I can get tasks`() = runBlocking {
        val now = OffsetDateTime.now()
        val t = task(now)

        val tasksRepository = TasksInMemoryRepository().withState(listOf(t))
        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)


        assertEquals(listOf(t), service.getTasks(null))
    }

    @Test
    fun `I can get tasks by id`() = runBlocking {
        val knownId = "1"
        val t = task(OffsetDateTime.now()).copy(id = knownId)

        val tasksRepository = TasksInMemoryRepository().withState(listOf(t))
        val service = TasksService(tasksRepository, dataCacheService, entitiesService, authService, seasonService)


        assertEquals(t, service.getTask(knownId))
    }

    @Test
    fun `run task to update Wow Mythic plus season`() {
        TODO()
    }
}