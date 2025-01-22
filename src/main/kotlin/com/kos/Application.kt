package com.kos

import com.kos.activities.ActivitiesController
import com.kos.activities.ActivitiesService
import com.kos.activities.repository.ActivitiesDatabaseRepository
import com.kos.auth.AuthController
import com.kos.auth.AuthService
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.entities.EntitiesService
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.clients.blizzard.BlizzardHttpAuthClient
import com.kos.clients.blizzard.BlizzardHttpClient
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.raiderio.RaiderIoHTTPClient
import com.kos.clients.riot.RiotHTTPClient
import com.kos.common.DatabaseFactory
import com.kos.common.JWTConfig
import com.kos.common.RetryConfig
import com.kos.common.launchSubscription
import com.kos.credentials.CredentialsController
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.EntitiesController
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.eventsourcing.subscriptions.EventSubscriptionController
import com.kos.eventsourcing.subscriptions.EventSubscriptionService
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsDatabaseRepository
import com.kos.plugins.*
import com.kos.roles.RolesController
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesDatabaseRepository
import com.kos.roles.repository.RolesDatabaseRepository
import com.kos.tasks.TasksController
import com.kos.tasks.TasksLauncher
import com.kos.tasks.TasksService
import com.kos.tasks.repository.TasksDatabaseRepository
import com.kos.views.ViewsController
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsDatabaseRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val riotApiKey = System.getenv("RIOT_API_KEY")

    val jwtConfig = JWTConfig(
        System.getenv("JWT_ISSUER"),
        System.getenv("JWT_SECRET")
    )

    val blizzardCredentials = BlizzardCredentials(
        System.getenv("BLIZZARD_CLIENT_ID"),
        System.getenv("BLIZZARD_CLIENT_SECRET")
    )

    val coroutineScope = CoroutineScope(Dispatchers.Default)

    val db = DatabaseFactory.pooledDatabase()

    val client = HttpClient(CIO)
    val raiderIoHTTPClient = RaiderIoHTTPClient(client)
    val riotHTTPClient = RiotHTTPClient(client, riotApiKey)
    val blizzardAuthClient = BlizzardHttpAuthClient(client, blizzardCredentials)
    val blizzardClient = BlizzardHttpClient(client, blizzardAuthClient)

    val eventStore = EventStoreDatabase(db)

    val credentialsRepository = CredentialsDatabaseRepository(db)
    val credentialsService = CredentialsService(credentialsRepository)
    val credentialsController = CredentialsController(credentialsService)

    val activitiesRepository = ActivitiesDatabaseRepository(db)
    val activitiesService = ActivitiesService(activitiesRepository)
    val activitiesController = ActivitiesController(activitiesService)

    val rolesRepository = RolesDatabaseRepository(db)
    val rolesActivitiesRepository = RolesActivitiesDatabaseRepository(db)
    val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
    val rolesController = RolesController(rolesService)

    val authRepository = AuthDatabaseRepository(db)
    val authService = AuthService(authRepository, credentialsService, rolesService, jwtConfig)
    val authController = AuthController(authService)

    val entitiesRepository = EntitiesDatabaseRepository(db)

    val viewsRepository = ViewsDatabaseRepository(db)
    val dataCacheRepository = DataCacheDatabaseRepository(db)
    val dataCacheRetryConfig = RetryConfig(3, 1200)
    val dataCacheService =
        DataCacheService(
            dataCacheRepository,
            entitiesRepository,
            raiderIoHTTPClient,
            riotHTTPClient,
            blizzardClient,
            dataCacheRetryConfig,
            eventStore
        )

    val entitiesService = EntitiesService(entitiesRepository, raiderIoHTTPClient, riotHTTPClient, blizzardClient)
    val entitiesController = EntitiesController(dataCacheService) //TODO: THIS FEELS WEIRD

    val viewsService =
        ViewsService(
            viewsRepository,
            entitiesService,
            dataCacheService,
            credentialsService,
            eventStore
        )
    val viewsController = ViewsController(viewsService)

    val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    val tasksRepository = TasksDatabaseRepository(db)
    val tasksService =
        TasksService(tasksRepository, dataCacheService, entitiesService, authService)
    val tasksLauncher =
        TasksLauncher(tasksService, tasksRepository, executorService, authService, dataCacheService, coroutineScope)
    val tasksController = TasksController(tasksService)

    coroutineScope.launch { tasksLauncher.launchTasks() }

    val subscriptionsRetryConfig = RetryConfig(10, 100)
    val subscriptionsRepository = SubscriptionsDatabaseRepository(db)
    val eventSubscriptionsService = EventSubscriptionService(subscriptionsRepository)
    val eventSubscriptionController = EventSubscriptionController(eventSubscriptionsService)

    val viewsEventSubscription = EventSubscription(
        "views",
        eventStore,
        subscriptionsRepository,
        subscriptionsRetryConfig
    ) { EventSubscription.viewsProcessor(it, viewsService) }

    val syncLolEventSubscription = EventSubscription(
        "sync-lol",
        eventStore,
        subscriptionsRepository,
        subscriptionsRetryConfig
    ) { EventSubscription.syncLolEntitiesProcessor(it, entitiesService, dataCacheService) }

    val syncWowEventSubscription = EventSubscription(
        "sync-wow",
        eventStore,
        subscriptionsRepository,
        subscriptionsRetryConfig
    ) { EventSubscription.syncWowEntitiesProcessor(it, entitiesService, dataCacheService) }

    val syncWowHardcoreEventSubscription = EventSubscription(
        "sync-wow-hc",
        eventStore,
        subscriptionsRepository,
        subscriptionsRetryConfig
    ) { EventSubscription.syncWowHardcoreEntitiesProcessor(it, entitiesService, dataCacheService) }

    val entitiesEventSubscription = EventSubscription(
        "entities",
        eventStore,
        subscriptionsRepository,
        subscriptionsRetryConfig
    ) { EventSubscription.entitiesProcessor(it, entitiesService) }

    launchSubscription(viewsEventSubscription)
    launchSubscription(syncLolEventSubscription)
    launchSubscription(syncWowEventSubscription)
    launchSubscription(syncWowHardcoreEventSubscription)
    launchSubscription(entitiesEventSubscription)

    configureAuthentication(credentialsService, jwtConfig)
    configureCors()
    configureRouting(
        activitiesController,
        authController,
        credentialsController,
        rolesController,
        viewsController,
        tasksController,
        eventSubscriptionController,
        entitiesController
    )
    configureSerialization()
    configureLogging()
}
