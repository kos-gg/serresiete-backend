package acceptance

import com.kos.activities.ActivitiesController
import com.kos.activities.ActivitiesService
import com.kos.activities.repository.ActivitiesDatabaseRepository
import com.kos.auth.AuthController
import com.kos.auth.AuthService
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.clients.RetryConfig
import com.kos.clients.blizzard.BlizzardHttpAuthClient
import com.kos.clients.blizzard.BlizzardHttpClient
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.raiderio.RaiderIoHTTPClient
import com.kos.clients.riot.RiotHTTPClient
import com.kos.common.JWTConfig
import com.kos.credentials.CredentialsController
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.datacache.DataCacheService
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.EntitiesController
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.wowguilds.WowGuildsDatabaseRepository
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.eventsourcing.subscriptions.EventSubscriptionController
import com.kos.eventsourcing.subscriptions.EventSubscriptionService
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsDatabaseRepository
import com.kos.eventsourcing.subscriptions.sync.*
import com.kos.plugins.configureAuthentication
import com.kos.plugins.configureCors
import com.kos.plugins.configureRouting
import com.kos.plugins.configureSerialization
import com.kos.roles.RolesController
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesDatabaseRepository
import com.kos.roles.repository.RolesDatabaseRepository
import com.kos.sources.SourcesController
import com.kos.sources.SourcesService
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowEntitySynchronizer
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionDatabaseRepository
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonDatabaseRepository
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.tasks.TasksController
import com.kos.tasks.TasksService
import com.kos.tasks.repository.TasksDatabaseRepository
import com.kos.views.ViewsController
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsDatabaseRepository
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

data class TestSubscriptions(
    val views: EventSubscription,
    val syncLol: EventSubscription,
    val syncWow: EventSubscription,
    val syncWowHc: EventSubscription,
    val entities: EventSubscription,
)

fun Application.testModule(db: Database, jwtConfig: JWTConfig): TestSubscriptions {

    val retryConfig = RetryConfig(maxAttempts = 1, delayTime = 0)
    val raiderIoHTTPClient = RaiderIoHTTPClient(mockHttpClient, retryConfig)
    val riotHTTPClient = RiotHTTPClient(mockHttpClient, retryConfig, "test-api-key")
    val blizzardAuthClient = BlizzardHttpAuthClient(mockHttpClient, BlizzardCredentials("id", "secret"))
    val blizzardClient = BlizzardHttpClient(mockHttpClient, retryConfig, blizzardAuthClient)

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
    val wowGuildsDatabaseRepository = WowGuildsDatabaseRepository(db)
    val wowItemsDatabaseRepository = WowItemsDatabaseRepository(db)
    val dataCacheRepository = DataCacheDatabaseRepository(db)
    val seasonRepository = WowSeasonDatabaseRepository(db)
    val staticDataRepository = WowExpansionDatabaseRepository(db)
    val viewsRepository = ViewsDatabaseRepository(db)

    val wowResolver = WowEntityResolver(entitiesRepository, raiderIoHTTPClient)
    val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
    val lolResolver = LolEntityResolver(entitiesRepository, riotHTTPClient)
    val entityResolverProvider = EntityResolverProvider(listOf(lolResolver, wowResolver, wowHardcoreResolver))

    val lolUpdater = LolEntityUpdater(riotHTTPClient, entitiesRepository)
    val lolEntitySynchronizer = LolEntitySynchronizer(dataCacheRepository, riotHTTPClient)
    val wowHardcoreEntitySynchronizer = WowHardcoreEntitySynchronizer(
        dataCacheRepository,
        entitiesRepository,
        raiderIoHTTPClient,
        blizzardClient,
        wowItemsDatabaseRepository
    )
    val wowEntitySynchronizer = WowEntitySynchronizer(dataCacheRepository, raiderIoHTTPClient, seasonRepository)
    val entitySynchronizerProvider =
        EntitySynchronizerProvider(listOf(lolEntitySynchronizer, wowHardcoreEntitySynchronizer, wowEntitySynchronizer))

    val wowSeasonService = WowSeasonService(staticDataRepository, seasonRepository, raiderIoHTTPClient)
    val dataCacheService = DataCacheService(dataCacheRepository, entitiesRepository, eventStore)

    val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)
    val entitiesService = EntitiesService(
        entitiesRepository,
        wowGuildsDatabaseRepository,
        entityResolverProvider,
        lolUpdater,
        wowHardcoreGuildUpdater
    )
    val entitiesController = EntitiesController(dataCacheService)

    val viewsService = ViewsService(viewsRepository, entitiesService, dataCacheService, credentialsService, eventStore)
    val viewsController = ViewsController(viewsService)

    val sourcesService = SourcesService(wowSeasonService)
    val sourcesController = SourcesController(sourcesService)

    val tasksRepository = TasksDatabaseRepository(db)
    val tasksService = TasksService(
        tasksRepository,
        dataCacheService,
        entitiesService,
        authService,
        wowSeasonService,
        entitySynchronizerProvider
    )
    val tasksController = TasksController(tasksService)

    val subscriptionsRepository = SubscriptionsDatabaseRepository(db)
    val eventSubscriptionsService = EventSubscriptionService(subscriptionsRepository)
    val eventSubscriptionController = EventSubscriptionController(eventSubscriptionsService)

    configureAuthentication(credentialsService, jwtConfig)
    configureSerialization()
    configureCors()
    configureRouting(
        activitiesController,
        authController,
        credentialsController,
        rolesController,
        viewsController,
        tasksController,
        eventSubscriptionController,
        entitiesController,
        sourcesController
    )

    return TestSubscriptions(
        views = EventSubscription("views", eventStore, subscriptionsRepository, retryConfig) {
            ViewsEventProcessor(it, viewsService).process()
        },
        syncLol = EventSubscription("sync-lol", eventStore, subscriptionsRepository, retryConfig) {
            LolEventProcessor(it, entitiesService, lolEntitySynchronizer).process()
        },
        syncWow = EventSubscription("sync-wow", eventStore, subscriptionsRepository, retryConfig) {
            WowEventProcessor(it, entitiesService, wowEntitySynchronizer).process()
        },
        syncWowHc = EventSubscription("sync-wow-hc", eventStore, subscriptionsRepository, retryConfig) {
            WowHardcoreEventProcessor(it, entitiesService, wowHardcoreEntitySynchronizer).process()
        },
        entities = EventSubscription("entities", eventStore, subscriptionsRepository, retryConfig) {
            EntitiesEventProcessor(it, entitiesService).process()
        },
    )
}
