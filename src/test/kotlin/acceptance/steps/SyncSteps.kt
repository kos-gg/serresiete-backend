package acceptance.steps

import acceptance.MockConfig
import acceptance.SharedInfrastructure
import acceptance.toGame
import acceptance.wowEntityRequest
import com.kos.clients.domain.HardcoreData
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.RequestToBeSynced
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.views.Game
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncSteps(private val scenarioVariables: acceptance.ScenarioVariables) {

    private val db = SharedInfrastructure.db
    private val eventStore = EventStoreDatabase(db)
    private val entitiesRepo = EntitiesDatabaseRepository(db)
    private val dataCacheRepo = DataCacheDatabaseRepository(db)

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

    @And("the Blizzard profile API returns 404")
    fun blizzardProfileReturns404() {
        MockConfig.blizzardProfileStatusOverride = HttpStatusCode.NotFound
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
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<HardcoreData>(cached.data)
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

    @Then("a failure event is saved for the operation")
    fun failureEventIsSaved() {
        val operationId = requireNotNull(scenarioVariables.operationId) { "No operationId in scenario" }
        runBlocking {
            val events = eventStore.getEventsByOperationId(operationId)
            assertTrue(
                events.any { it.event.eventData.eventType == EventType.OPERATION_FAILED },
                "Expected OPERATION_FAILED event for operation $operationId but found: ${events.map { it.event.eventData.eventType }}"
            )
        }
    }

    @Then("a completed event is saved for the operation")
    fun completedEventIsSaved() {
        val operationId = requireNotNull(scenarioVariables.operationId) { "No operationId in scenario" }
        runBlocking {
            val events = eventStore.getEventsByOperationId(operationId)
            val completionEventTypes = setOf(EventType.VIEW_SYNC_COMPLETED, EventType.VIEW_DELETED)
            assertTrue(
                events.any { it.event.eventData.eventType in completionEventTypes },
                "Expected completion event for operation $operationId but found: ${events.map { it.event.eventData.eventType }}"
            )
        }
    }

    private fun getWowEntityRequest(game: String, name: String, realmOrTag: String, region: String): Pair<Game, EntityRequest> {
        val resolvedGame = game.toGame()
        val request: EntityRequest = when (resolvedGame) {
            Game.WOW, Game.WOW_HC -> wowEntityRequest(name, realmOrTag, region)
            else -> throw IllegalArgumentException("Unknown game $resolvedGame")
        }
        return Pair(resolvedGame, request)
    }
}
