package com.kos.tasks

import arrow.core.Either
import com.kos.auth.AuthService
import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.characters.CharactersService
import com.kos.characters.CharactersTestHelper
import com.kos.characters.CharactersTestHelper.basicLolCharacter
import com.kos.characters.CharactersTestHelper.basicWowCharacter
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersState
import com.kos.clients.blizzard.BlizzardClient
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
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TasksServiceTest {
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)
    private val riotClient = Mockito.mock(RiotClient::class.java)
    private val blizzardClient = Mockito.mock(BlizzardClient::class.java)
    private val retryConfig = RetryConfig(1, 1)

    @Test
    fun `token cleanup task should cleanup tokens`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val charactersRepository = CharactersInMemoryRepository()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                charactersRepository,
                raiderIoClient,
                riotClient,
                blizzardClient,
                retryConfig
            )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

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
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

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
            val charactersRepository = CharactersInMemoryRepository()
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                charactersRepository,
                raiderIoClient,
                riotClient,
                blizzardClient,
                retryConfig
            )

            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val now = OffsetDateTime.now()
            val expectedRemainingTask = task(now)
            val tasksRepository =
                TasksInMemoryRepository().withState(listOf(expectedRemainingTask, task(now.minusDays(8))))
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

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
    fun `data cache wow task should cache wow characters`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val charactersRepository =
                CharactersInMemoryRepository().withState(CharactersState(listOf(basicWowCharacter), listOf(), listOf()))
            val dataCacheService = DataCacheService(
                dataCacheRepository,
                charactersRepository,
                raiderIoClient,
                riotClient,
                blizzardClient,
                retryConfig
            )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

            `when`(raiderIoClient.get(basicWowCharacter)).thenReturn(RaiderIoMockHelper.get(basicWowCharacter))
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
    fun `data cache lol task should cache lol characters`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val charactersRepository =
                CharactersInMemoryRepository().withState(CharactersState(listOf(), listOf(), listOf(basicLolCharacter)))
            val dataCacheService =
                DataCacheService(
                    dataCacheRepository,
                    charactersRepository,
                    raiderIoClient,
                    riotClient,
                    blizzardClient,
                    retryConfig
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

            `when`(riotClient.getLeagueEntriesBySummonerId(basicLolCharacter.summonerId)).thenReturn(RiotMockHelper.leagueEntries)
            `when`(riotClient.getMatchesByPuuid(basicLolCharacter.puuid, QueueType.SOLO_Q.toInt())).thenReturn(
                RiotMockHelper.matches
            )
            `when`(riotClient.getMatchesByPuuid(basicLolCharacter.puuid, QueueType.FLEX_Q.toInt())).thenReturn(
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
    fun `update lol characters task should update lol characters correctly`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val charactersRepository =
                CharactersInMemoryRepository().withState(CharactersState(listOf(), listOf(), listOf(basicLolCharacter)))
            val dataCacheService =
                DataCacheService(
                    dataCacheRepository,
                    charactersRepository,
                    raiderIoClient,
                    riotClient,
                    blizzardClient,
                    retryConfig
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

            `when`(riotClient.getSummonerByPuuid(basicLolCharacter.puuid)).thenReturn(Either.Right(CharactersTestHelper.basicGetSummonerResponse))
            `when`(riotClient.getAccountByPUUID(basicLolCharacter.puuid)).thenReturn(Either.Right(CharactersTestHelper.basicGetAccountResponse))

            val id = UUID.randomUUID().toString()

            service.updateLolCharacters(id)

            val insertedTask = tasksRepository.state().first()

            assertEquals(1, tasksRepository.state().size)
            assertEquals(id, insertedTask.id)
            assertEquals(Status.SUCCESSFUL, insertedTask.taskStatus.status)
            assertEquals(TaskType.UPDATE_LOL_CHARACTERS_TASK, insertedTask.type)
        }
    }

    @Test
    fun `run task with correct parameters should run token cleanup task`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository()
            val charactersRepository = CharactersInMemoryRepository()
            val dataCacheService =
                DataCacheService(
                    dataCacheRepository,
                    charactersRepository,
                    raiderIoClient,
                    riotClient,
                    blizzardClient,
                    retryConfig
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

            val id = UUID.randomUUID().toString()

            service.runTask(TaskType.TOKEN_CLEANUP_TASK, id)

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
            val charactersRepository = CharactersInMemoryRepository()
            val dataCacheService =
                DataCacheService(
                    dataCacheRepository,
                    charactersRepository,
                    raiderIoClient,
                    riotClient,
                    blizzardClient,
                    retryConfig
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())

            val id = UUID.randomUUID().toString()

            service.runTask(TaskType.CACHE_WOW_DATA_TASK, id)

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
            val charactersRepository = CharactersInMemoryRepository()
            val dataCacheService =
                DataCacheService(
                    dataCacheRepository,
                    charactersRepository,
                    raiderIoClient,
                    riotClient,
                    blizzardClient,
                    retryConfig
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val credentialsService = CredentialsService(credentialsRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository()
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)

            val id = UUID.randomUUID().toString()

            service.runTask(TaskType.CACHE_LOL_DATA_TASK, id)

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
            val charactersRepository = CharactersInMemoryRepository()
            val dataCacheService =
                DataCacheService(
                    dataCacheRepository,
                    charactersRepository,
                    raiderIoClient,
                    riotClient,
                    blizzardClient,
                    retryConfig
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val credentialsService = CredentialsService(credentialsRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))


            val tasksRepository = TasksInMemoryRepository().withState(listOf(task))
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)
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
            val charactersRepository = CharactersInMemoryRepository()
            val dataCacheService =
                DataCacheService(
                    dataCacheRepository,
                    charactersRepository,
                    raiderIoClient,
                    riotClient,
                    blizzardClient,
                    retryConfig
                )
            val charactersService = CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)

            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val credentialsService = CredentialsService(credentialsRepository)
            val authRepository = AuthInMemoryRepository()
            val authService =
                AuthService(authRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            val tasksRepository = TasksInMemoryRepository().withState(listOf(task))
            val service = TasksService(tasksRepository, dataCacheService, charactersService, authService)
            assertEquals(task, service.getTask(knownId))
        }
    }
}