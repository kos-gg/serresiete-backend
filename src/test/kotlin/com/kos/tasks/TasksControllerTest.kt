package com.kos.tasks

import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.auth.AuthService
import com.kos.auth.Authorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.characters.CharactersService
import com.kos.characters.CharactersTestHelper.emptyCharactersState
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersState
import com.kos.common.JWTConfig
import com.kos.common.RetryConfig
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper
import com.kos.credentials.CredentialsTestHelper.emptyCredentialsState
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.datacache.DataCache
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.roles.Role
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TasksControllerTest {
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)
    private val riotClient = Mockito.mock(RiotClient::class.java)
    private val blizzardClient = Mockito.mock(BlizzardClient::class.java)
    private val retryConfig = RetryConfig(1, 1)

    private val charactersRepository = CharactersInMemoryRepository()
    private val dataCacheRepository = DataCacheInMemoryRepository()
    private val credentialsRepository = CredentialsInMemoryRepository()
    private val rolesRepository = RolesInMemoryRepository()
    private val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
    private val tasksRepository = TasksInMemoryRepository()
    private val authRepository = AuthInMemoryRepository()

    private suspend fun createController(
        credentialsState: CredentialsRepositoryState,
        tasksState: List<Task>,
        charactersState: CharactersState,
        dataCacheState: List<DataCache>,
        authState: List<Authorization>,
        rolesState: List<Role>,
        rolesActivitiesState: Map<Role, Set<Activity>>
    ): TasksController {
        val charactersRepositoryWithState = charactersRepository.withState(charactersState)
        val dataCacheRepositoryWithState = dataCacheRepository.withState(dataCacheState)
        val credentialsRepositoryWithState = credentialsRepository.withState(credentialsState)
        val rolesActivitiesRepositoryWithState = rolesActivitiesRepository.withState(rolesActivitiesState)
        val tasksRepositoryWithState = tasksRepository.withState(tasksState)
        val authRepositoryWithState = authRepository.withState(authState)
        val rolesRepositoryWithState = rolesRepository.withState(rolesState)


        val rolesService = RolesService(rolesRepositoryWithState, rolesActivitiesRepositoryWithState)
        val credentialsService = CredentialsService(credentialsRepositoryWithState)
        val dataCacheService = DataCacheService(dataCacheRepositoryWithState, charactersRepositoryWithState, raiderIoClient, riotClient, blizzardClient, retryConfig)
        val charactersService = CharactersService(charactersRepositoryWithState, raiderIoClient, riotClient, blizzardClient)
        val authService =
            AuthService(authRepositoryWithState, credentialsService, rolesService, JWTConfig("issuer", "secret"))
        val tasksService = TasksService(tasksRepositoryWithState, dataCacheService, charactersService, authService)

        return TasksController(tasksService)
    }

    @Test
    fun `i can get tasks`() {
        runBlocking {
            val now = OffsetDateTime.now()

            val task = task(now)
            val controller = createController(
                emptyCredentialsState,
                listOf(task),
                emptyCharactersState,
                listOf(),
                listOf(),
                listOf(),
                mapOf()
            )
            assertEquals(listOf(task), controller.getTasks("owner", setOf(Activities.getTasks), null).getOrNull())
        }
    }

    @Test
    fun `i can get task by id`() {
        runBlocking {
            val now = OffsetDateTime.now()
            val credentialsState = CredentialsRepositoryState(
                listOf(CredentialsTestHelper.basicCredentials.copy(userName = "owner")),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val knownId = "1"
            val task = task(now).copy(id = knownId)
            val controller = createController(
                credentialsState,
                listOf(task),
                emptyCharactersState,
                listOf(),
                listOf(),
                listOf(),
                mapOf()
            )
            assertEquals(task, controller.getTask("owner", knownId, setOf(Activities.getTask)).getOrNull())
        }
    }
}