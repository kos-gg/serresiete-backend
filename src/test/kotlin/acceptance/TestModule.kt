package acceptance

import com.kos.activities.ActivitiesController
import com.kos.activities.ActivitiesService
import com.kos.activities.repository.ActivitiesDatabaseRepository
import com.kos.auth.AuthController
import com.kos.auth.AuthService
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.clients.blizzard.BlizzardHttpAuthClient
import com.kos.clients.blizzard.BlizzardHttpClient
import com.kos.clients.config.RetryConfig
import com.kos.clients.config.blizzardHttpClientConfig
import com.kos.clients.config.raiderIoHttpClientConfig
import com.kos.clients.config.riotHttpClientConfig
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.raiderio.RaiderIoHttpClient
import com.kos.clients.riot.RiotHttpClient
import com.kos.common.JWTConfig
import com.kos.credentials.CredentialsController
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.EntitiesController
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.wowguilds.WowGuildsDatabaseRepository
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.entities.sync.SyncBudget
import com.kos.entities.sync.SyncEntitySelector
import com.kos.entities.sync.rules.StalenessSyncRule
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.eventsourcing.subscriptions.EventSubscriptionController
import com.kos.eventsourcing.subscriptions.EventSubscriptionService
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsDatabaseRepository
import com.kos.eventsourcing.subscriptions.sync.EntitiesEventProcessor
import com.kos.eventsourcing.subscriptions.sync.GameSyncEventProcessor
import com.kos.eventsourcing.subscriptions.sync.ViewsEventProcessor
import com.kos.plugins.*
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
import com.kos.sources.wow.WowGuildResolver
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionDatabaseRepository
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonDatabaseRepository
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowforever.WowForeverEntityResolver
import com.kos.sources.wowforever.WowForeverEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreGuildResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.tasks.TaskType
import com.kos.tasks.TasksController
import com.kos.tasks.TasksService
import com.kos.tasks.repository.TasksDatabaseRepository
import com.kos.tasks.runners.*
import com.kos.views.Game
import com.kos.views.ViewsController
import com.kos.views.ViewsEventService
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsDatabaseRepository
import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

data class TestSubscriptions(
    val views: EventSubscription,
    val syncLol: EventSubscription,
    val syncWow: EventSubscription,
    val syncWowHc: EventSubscription,
    val syncWowForever: EventSubscription,
    val entities: EventSubscription,
)

fun Application.testModule(db: Database, jwtConfig: JWTConfig): TestSubscriptions {

    val retryConfig = RetryConfig(maxRetries = 1, delayTime = 0)
    val raiderIoHttpClient = RaiderIoHttpClient(HttpClient(mockHttpClient.engine) {
        raiderIoHttpClientConfig(
            retryConfig,
            apiKey = "test-key"
        )
    })
    val riotHttpClient =
        RiotHttpClient(HttpClient(mockHttpClient.engine) { riotHttpClientConfig(retryConfig, "test-api-key") })
    val blizzardAuthClient = BlizzardHttpAuthClient(mockHttpClient, BlizzardCredentials("id", "secret"))
    val blizzardClient = BlizzardHttpClient(HttpClient(mockHttpClient.engine) {
        blizzardHttpClientConfig(
            retryConfig,
            blizzardAuthClient
        )
    })

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

    val wowResolver = WowEntityResolver(entitiesRepository, blizzardClient)
    val wowGuildResolver = WowGuildResolver(entitiesRepository, blizzardClient, seasonRepository)
    val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
    val wowHardcoreGuildResolver = WowHardcoreGuildResolver(blizzardClient, wowHardcoreResolver)
    val lolResolver = LolEntityResolver(entitiesRepository, riotHttpClient)
    val wowForeverResolver = WowForeverEntityResolver(entitiesRepository, blizzardClient)
    val entityResolverProvider = EntityResolverProvider(
        wowResolver = wowResolver,
        wowGuildResolver = wowGuildResolver,
        wowHardcoreResolver = wowHardcoreResolver,
        wowHardcoreGuildResolver = wowHardcoreGuildResolver,
        lolResolver = lolResolver,
        wowForeverResolver = wowForeverResolver
    )

    val lolUpdater = LolEntityUpdater(riotHttpClient, entitiesRepository)
    val lolEntitySynchronizer = LolEntitySynchronizer(dataCacheRepository, riotHttpClient)
    val wowHardcoreEntitySynchronizer = WowHardcoreEntitySynchronizer(
        dataCacheRepository,
        entitiesRepository,
        raiderIoHttpClient,
        blizzardClient,
        wowItemsDatabaseRepository
    )
    val wowEntitySynchronizer =
        WowEntitySynchronizer(dataCacheRepository, raiderIoHttpClient, blizzardClient, seasonRepository)
    val wowForeverEntitySynchronizer = WowForeverEntitySynchronizer(dataCacheRepository, blizzardClient)
    val entitySynchronizerProvider =
        EntitySynchronizerProvider(
            wowSynchronizer = wowEntitySynchronizer,
            wowHardcoreSynchronizer = wowHardcoreEntitySynchronizer,
            lolSynchronizer = lolEntitySynchronizer,
            wowForeverSynchronizer = wowForeverEntitySynchronizer
        )

    val wowSeasonService = WowSeasonService(staticDataRepository, seasonRepository, raiderIoHttpClient)
    val dataCacheService = DataCacheService(dataCacheRepository, entitiesRepository, eventStore)

    val wowHardcoreGuildUpdater =
        WowHardcoreGuildUpdater(wowHardcoreGuildResolver, wowHardcoreResolver, entitiesRepository, viewsRepository)
    val wowGuildUpdater = WowGuildUpdater(wowGuildResolver, entitiesRepository, viewsRepository)
    val entitiesService = EntitiesService(
        entitiesRepository,
        wowGuildsDatabaseRepository,
        entityResolverProvider,
        wowGuildResolver,
        lolUpdater,
        wowHardcoreGuildUpdater,
        wowGuildUpdater
    )
    val entitiesController = EntitiesController(dataCacheService, entitiesService)

    val viewsService = ViewsService(viewsRepository, entitiesService, dataCacheService, credentialsService, eventStore)
    val viewsEventService = ViewsEventService(viewsRepository, entitiesService)
    val viewsController = ViewsController(viewsService)

    val sourcesService = SourcesService(wowSeasonService)
    val sourcesController = SourcesController(sourcesService)

    val stalenessRule = StalenessSyncRule(
        entitiesRepository,
        30,
        SyncBudget(
            mapOf(
                Game.LOL to Int.MAX_VALUE,
                Game.WOW to Int.MAX_VALUE,
                Game.WOW_HC to Int.MAX_VALUE,
                Game.WOW_FOREVER to Int.MAX_VALUE
            )
        )
    )
    val syncEntitySelector = SyncEntitySelector(stalenessRule)

    val tasksRepository = TasksDatabaseRepository(db)
    val taskRunnerProvider = TaskRunnerProvider(
        listOf(
            TokenCleanupTaskRunner(authService),
            TaskCleanupTaskRunner(tasksRepository),
            UpdateLolEntitiesTaskRunner(entitiesService),
            CacheClearTaskRunner(dataCacheService),
            UpdateWowHardcoreGuildsTaskRunner(entitiesService),
            UpdateWowGuildsTaskRunner(entitiesService),
            UpdateMythicPlusSeasonTaskRunner(wowSeasonService),
            CacheGameDataTaskRunner(
                Game.LOL,
                TaskType.CACHE_LOL_DATA_TASK,
                syncEntitySelector,
                entitySynchronizerProvider
            ),
            CacheGameDataTaskRunner(
                Game.WOW,
                TaskType.CACHE_WOW_DATA_TASK,
                syncEntitySelector,
                entitySynchronizerProvider
            ),
            CacheGameDataTaskRunner(
                Game.WOW_HC,
                TaskType.CACHE_WOW_HC_DATA_TASK,
                syncEntitySelector,
                entitySynchronizerProvider
            ),
            CacheGameDataTaskRunner(
                Game.WOW_FOREVER,
                TaskType.CACHE_WOW_FOREVER_DATA_TASK,
                syncEntitySelector,
                entitySynchronizerProvider
            ),
            CacheGameViewDataTaskRunner(
                viewsService,
                entitiesService,
                entitySynchronizerProvider,
                300L
            )
        )
    )
    val tasksService = TasksService(tasksRepository, taskRunnerProvider)
    val tasksController = TasksController(tasksService)

    val subscriptionsRepository = SubscriptionsDatabaseRepository(db)
    val eventSubscriptionsService = EventSubscriptionService(subscriptionsRepository)
    val eventSubscriptionController = EventSubscriptionController(eventSubscriptionsService)

    val operationsService = com.kos.operations.OperationsService(eventStore)
    val operationsController = com.kos.operations.OperationsController(operationsService)

    configureAuthentication(credentialsService, jwtConfig)
    configureSerialization()
    configureCors()
    configureStatusPages()
    configureRouting(
        activitiesController,
        authController,
        credentialsController,
        rolesController,
        viewsController,
        tasksController,
        eventSubscriptionController,
        entitiesController,
        sourcesController,
        operationsController
    )

    return TestSubscriptions(
        views = EventSubscription("views", eventStore, subscriptionsRepository) {
            ViewsEventProcessor(it, viewsEventService).process()
        },
        syncLol = EventSubscription("sync-lol", eventStore, subscriptionsRepository) {
            GameSyncEventProcessor(it, entitiesService, lolEntitySynchronizer).process()
        },
        syncWow = EventSubscription("sync-wow", eventStore, subscriptionsRepository) {
            GameSyncEventProcessor(it, entitiesService, wowEntitySynchronizer).process()
        },
        syncWowHc = EventSubscription("sync-wow-hc", eventStore, subscriptionsRepository) {
            GameSyncEventProcessor(it, entitiesService, wowHardcoreEntitySynchronizer).process()
        },
        syncWowForever = EventSubscription("sync-wow-forever", eventStore, subscriptionsRepository) {
            GameSyncEventProcessor(it, entitiesService, wowForeverEntitySynchronizer).process()
        },
        entities = EventSubscription("entities", eventStore, subscriptionsRepository) {
            EntitiesEventProcessor(it, entitiesService).process()
        },
    )
}
