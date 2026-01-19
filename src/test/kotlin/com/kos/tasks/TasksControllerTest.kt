package com.kos.tasks

import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.auth.AuthService
import com.kos.auth.Authorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.JWTConfig
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper
import com.kos.credentials.CredentialsTestHelper.emptyCredentialsState
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.datacache.DataCache
import com.kos.datacache.DataCacheService
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.roles.Role
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowEntitySynchronizer
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.tasks.TasksTestHelper.task
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TasksControllerTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val wowItemsDatabaseRepository = mock(WowItemsDatabaseRepository::class.java)

    private val entitiesRepository = EntitiesInMemoryRepository()
    private val dataCacheRepository = DataCacheInMemoryRepository()
    private val credentialsRepository = CredentialsInMemoryRepository()
    private val rolesRepository = RolesInMemoryRepository()
    private val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
    private val tasksRepository = TasksInMemoryRepository()
    private val authRepository = AuthInMemoryRepository()
    private val staticDataRepository = WowExpansionInMemoryRepository()
    private val seasonDatabaseRepository = WowSeasonInMemoryRepository()

    @Test
    fun `i can get tasks`() {
        runBlocking {
            val now = OffsetDateTime.now()

            val task = task(now)
            val controller = createController(
                emptyCredentialsState,
                listOf(task),
                emptyEntitiesState,
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
                emptyEntitiesState,
                listOf(),
                listOf(),
                listOf(),
                mapOf()
            )
            assertEquals(task, controller.getTask("owner", knownId, setOf(Activities.getTask)).getOrNull())
        }
    }

    private suspend fun createController(
        credentialsState: CredentialsRepositoryState,
        tasksState: List<Task>,
        entitiesState: EntitiesState,
        dataCacheState: List<DataCache>,
        authState: List<Authorization>,
        rolesState: List<Role>,
        rolesActivitiesState: Map<Role, Set<Activity>>
    ): TasksController {
        val entitiesRepositoryWithState = entitiesRepository.withState(entitiesState)
        val dataCacheRepositoryWithState = dataCacheRepository.withState(dataCacheState)
        val credentialsRepositoryWithState = credentialsRepository.withState(credentialsState)
        val rolesActivitiesRepositoryWithState = rolesActivitiesRepository.withState(rolesActivitiesState)
        val tasksRepositoryWithState = tasksRepository.withState(tasksState)
        val authRepositoryWithState = authRepository.withState(authState)
        val rolesRepositoryWithState = rolesRepository.withState(rolesState)
        val eventStore = EventStoreInMemory()

        val wowGuildsRepository = WowGuildsInMemoryRepository()
        val viewsRepository = ViewsInMemoryRepository()

        val wowResolver = WowEntityResolver(entitiesRepository, raiderIoClient)
        val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
        val lolResolver = LolEntityResolver(entitiesRepository, riotClient)

        val entitiesResolver = EntityResolverProvider(
            listOf(
                wowResolver,
                wowHardcoreResolver,
                lolResolver
            )
        )

        val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)

        val rolesService = RolesService(rolesRepositoryWithState, rolesActivitiesRepositoryWithState)
        val credentialsService = CredentialsService(credentialsRepositoryWithState)

        val dataCacheService = DataCacheService(
            dataCacheRepositoryWithState,
            entitiesRepositoryWithState,
            eventStore
        )
        val entitiesService =
            EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )
        val authService =
            AuthService(authRepositoryWithState, credentialsService, rolesService, JWTConfig("issuer", "secret"))
        val entityCacheServiceRegistry = EntitySynchronizerProvider(
            listOf(
                LolEntitySynchronizer(dataCacheRepository, riotClient),
                WowHardcoreEntitySynchronizer(
                    dataCacheRepository,
                    entitiesRepositoryWithState,
                    raiderIoClient,
                    blizzardClient,
                    wowItemsDatabaseRepository,

                    ),
                WowEntitySynchronizer(dataCacheRepository, raiderIoClient)
            )
        )

        val seasonService =
            WowSeasonService(staticDataRepository, seasonDatabaseRepository, raiderIoClient)
        val tasksService =
            TasksService(
                tasksRepositoryWithState,
                dataCacheService,
                entitiesService,
                authService,
                seasonService,
                entityCacheServiceRegistry
            )

        return TasksController(tasksService)
    }
}