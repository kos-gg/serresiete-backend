package acceptance.steps

import acceptance.MockConfig
import acceptance.SharedInfrastructure
import acceptance.toGame
import acceptance.wowEntityRequest
import acceptance.wowForeverEntityRequest
import com.kos.datacache.domain.WowForeverData
import com.kos.datacache.domain.WowHardcoreData
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.RequestToBeSynced
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.sources.wowforever.media.WowForeverMediaDatabaseRepository
import com.kos.sources.wowforever.media.WowForeverMediaKind
import com.kos.views.Game
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncSteps(private val scenarioVariables: acceptance.ScenarioVariables) {

    private val db = SharedInfrastructure.db
    private val eventStore = EventStoreDatabase(db)
    private val entitiesRepo = EntitiesDatabaseRepository(db)
    private val dataCacheRepo = DataCacheDatabaseRepository(db)
    private val mediaRepo = WowForeverMediaDatabaseRepository(db)

    @Given("a {string} sync event is posted for {string} {string} {string}")
    fun syncEventPosted(game: String, name: String, realm: String, region: String) {
        val (resolvedGame, request) = getWowEntityRequest(game, name, realm, region)
        val operationId = UUID.randomUUID().toString()
        scenarioVariables.operationId = operationId
        runBlocking {
            eventStore.save(Event("/entity/-1", operationId, RequestToBeSynced(request, resolvedGame)))
        }
    }

    @Given("a LOL sync event is posted for {string} {string}")
    fun lolSyncEventPosted(name: String, tag: String) {
        val request: EntityRequest = LolEntityRequest(name, tag)
        val operationId = UUID.randomUUID().toString()
        scenarioVariables.operationId = operationId
        runBlocking {
            eventStore.save(Event("/entity/-1", operationId, RequestToBeSynced(request, Game.LOL)))
        }
    }

    @When("the WOW sync subscription processes pending events")
    fun syncWowProcessesPendingEvents() {
        runBlocking { SharedInfrastructure.subscriptions.syncWow.processPendingEvents() }
    }

    @When("the WOW HC sync subscription processes pending events")
    fun syncWowHcProcessesPendingEvents() {
        runBlocking { SharedInfrastructure.subscriptions.syncWowHc.processPendingEvents() }
    }

    @When("the WOW_FOREVER sync subscription processes pending events")
    fun syncWowForeverProcessesPendingEvents() {
        runBlocking { SharedInfrastructure.subscriptions.syncWowForever.processPendingEvents() }
    }

    @When("the LOL sync subscription processes pending events")
    fun syncLolProcessesPendingEvents() {
        runBlocking { SharedInfrastructure.subscriptions.syncLol.processPendingEvents() }
    }

    @Then("the data cache contains a {string} entry for {string} {string} {string}")
    fun dataCacheContainsEntry(game: String, name: String, realmOrTag: String, region: String) {
        val (resolvedGame, request) = getWowEntityRequest(game, name, realmOrTag, region)
        runBlocking {
            val entity = entitiesRepo.get(request, resolvedGame)
            assertNotNull(entity, "Entity $name not found in database after sync")
            val cached = dataCacheRepo.get(entity.id)
            assertNotNull(cached.firstOrNull(), "No data cache entry found for entity $name after sync")
        }
    }

    @Then("the WOW_FOREVER data cache for {string} {string} {string} has items and talents with icons")
    fun wowForeverCacheHasItemsAndTalentsWithIcons(name: String, surname: String, region: String) {
        val data = newestWowForeverData(name, surname, region)
        val talents = data.talents.orEmpty().flatMap { it.talents }
        assertTrue(data.items.orEmpty().isNotEmpty(), "Expected items in data cache of $name")
        assertTrue(talents.isNotEmpty(), "Expected talents in data cache of $name")
        assertTrue(data.items.orEmpty().all { it.icon != null }, "Expected every item to have an icon: ${data.items}")
        assertTrue(talents.all { it.icon != null }, "Expected every talent to have an icon: $talents")
    }

    @And("the WOW_FOREVER media table stores the icons of {string} {string} {string}")
    fun wowForeverMediaStoresIcons(name: String, surname: String, region: String) {
        val data = newestWowForeverData(name, surname, region)
        val items = data.items.orEmpty()
        val talents = data.talents.orEmpty().flatMap { it.talents }
        runBlocking {
            assertEquals(
                items.associate { it.id to it.icon },
                mediaRepo.getIcons(WowForeverMediaKind.ITEM, items.map { it.id }.toSet())
            )
            assertEquals(
                talents.associate { it.spellId to it.icon },
                mediaRepo.getIcons(WowForeverMediaKind.SPELL, talents.map { it.spellId }.toSet())
            )
        }
    }

    private fun newestWowForeverData(name: String, surname: String, region: String): WowForeverData =
        runBlocking {
            val entity = entitiesRepo.get(wowForeverEntityRequest(name, surname, region) as EntityRequest, Game.WOW_FOREVER)
            assertNotNull(entity, "Entity $name not found in database")
            val cached = dataCacheRepo.get(entity.id).maxByOrNull { it.inserted }
            assertNotNull(cached, "No data cache entry found for entity $name")
            Json { ignoreUnknownKeys = true }.decodeFromString<WowForeverData>(cached.data)
        }

    @And("the Blizzard profile API returns 404")
    fun blizzardProfileReturns404() {
        MockConfig.blizzardProfileStatusOverride = HttpStatusCode.NotFound
    }

    @And("the Blizzard profile API returns an error")
    fun blizzardProfileReturnsError() {
        MockConfig.blizzardProfileStatusOverride = HttpStatusCode.InternalServerError
    }

    @Then("the WOW_HC data cache for {string} {string} {string} has not been updated")
    fun wowHcCacheHasNotBeenUpdated(name: String, realm: String, region: String) {
        val request = wowEntityRequest(name, realm, region)
        runBlocking {
            val entity = entitiesRepo.get(request as EntityRequest, Game.WOW_HC)
            assertNotNull(entity, "Entity $name not found in database")
            val entries = dataCacheRepo.get(entity.id)
            assertTrue(entries.size == 1, "Expected exactly 1 cache entry (no sync), but found ${entries.size}")
        }
    }

    @Then("{string} {string} {string} is marked as dead in the WOW_HC data cache")
    fun characterIsMarkedAsDeadInCache(name: String, realm: String, region: String) {
        val request = wowEntityRequest(name, realm, region)
        runBlocking {
            val entity = entitiesRepo.get(request as EntityRequest, Game.WOW_HC)
            assertNotNull(entity, "Entity $name not found in database")
            val cached = dataCacheRepo.get(entity.id).maxByOrNull { it.inserted }
            assertNotNull(cached, "No data cache entry found for entity $name")
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<WowHardcoreData>(cached.data)
            assertTrue(data.isDead, "Expected character $name to be marked as dead in data cache")
        }
    }

    @Then("the LOL data cache contains an entry for {string} {string}")
    fun lolDataCacheContainsEntry(name: String, tag: String) {
        val request: EntityRequest = LolEntityRequest(name, tag)
        runBlocking {
            val entity = entitiesRepo.get(request, Game.LOL)
            assertNotNull(entity, "Entity $name not found in database after sync")
            val cached = dataCacheRepo.get(entity.id)
            assertNotNull(cached.firstOrNull(), "No data cache entry found for entity $name after sync")
        }
    }

    @And("the raiderIo cutoff API returns an error")
    fun raiderIoCutoffReturnsError() {
        MockConfig.raiderIoCutoffStatusOverride = HttpStatusCode.InternalServerError
    }

    @And("the raiderIo profile API returns an error")
    fun raiderIoProfileReturnsError() {
        MockConfig.raiderIoProfileStatusOverride = HttpStatusCode.InternalServerError
    }

    @Then("a completed event is saved for the operation")
    fun completedEventIsSaved() {
        val operationId = requireNotNull(scenarioVariables.operationId) { "No operationId in scenario" }
        runBlocking {
            val events = eventStore.getEventsByOperationId(operationId)
            val completionEventTypes =
                setOf(EventType.VIEW_SYNC_COMPLETED, EventType.VIEW_DELETED, EventType.ENTITY_SYNC_COMPLETED)
            assertTrue(
                events.any { it.event.eventData.eventType in completionEventTypes },
                "Expected completion event for operation $operationId but found: ${events.map { it.event.eventData.eventType }}"
            )
        }
    }

    private fun getWowEntityRequest(
        game: String,
        name: String,
        realmOrTag: String,
        region: String
    ): Pair<Game, EntityRequest> {
        val resolvedGame = game.toGame()
        val request: EntityRequest = when (resolvedGame) {
            Game.WOW, Game.WOW_HC -> wowEntityRequest(name, realmOrTag, region)
            Game.WOW_FOREVER -> wowForeverEntityRequest(name, realmOrTag, region)
            else -> throw IllegalArgumentException("Unknown game $resolvedGame")
        }
        return Pair(resolvedGame, request)
    }
}
