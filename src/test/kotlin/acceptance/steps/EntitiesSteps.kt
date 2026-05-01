package acceptance.steps

import acceptance.SharedInfrastructure
import acceptance.World
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.views.Game
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EntitiesSteps(private val world: World) {

    private val client = SharedInfrastructure.client
    private val db = SharedInfrastructure.db

    @Given("a {string} entity {string} on realm {string} region {string} exists with cached data {string}")
    fun entityExistsWithCachedData(game: String, name: String, realm: String, region: String, jsonFile: String) {
        val resolvedGame = Game.valueOf(game.uppercase())
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val dataCacheRepo = DataCacheDatabaseRepository(db)

        val entity = runBlocking {
            entitiesRepo.insert(listOf(WowEntityRequest(name, region, realm)), resolvedGame)
        }.getOrNull()!!.first()

        val data = javaClass.getResourceAsStream("/acceptance/files/entity/$jsonFile.json")!!
            .bufferedReader()
            .readText()

        runBlocking {
            dataCacheRepo.insert(listOf(DataCache(entity.id, data, OffsetDateTime.now(), resolvedGame)))
        }
    }

    @When("they search for a {string} entity {string} on realm {string} region {string}")
    fun searchEntity(game: String, name: String, realm: String, region: String) {
        world.response = runBlocking {
            client.get("/api/entities") {
                world.token?.let { bearerAuth(it) }
                parameter("game", game.lowercase())
                parameter("name", name)
                parameter("realm", realm)
                parameter("region", region)
            }
        }
    }

    @And("the response data is null")
    fun responseDataIsNull() {
        val body = runBlocking { world.response.bodyAsText() }
        val data = Json.parseToJsonElement(body).jsonObject["data"]
        assertNull(data?.takeIf { it.toString() != "null" })
    }

    @And("the response contains entity data")
    fun responseContainsEntityData() {
        val body = runBlocking { world.response.bodyAsText() }
        val data = Json.parseToJsonElement(body).jsonObject["data"]
        assertNotNull(data?.takeIf { it.toString() != "null" })
    }

    @And("the response data is a valid WOW entity")
    fun responseDataIsValidWowEntity() {
        val body = runBlocking { world.response.bodyAsText() }
        val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject
        assertEquals("com.kos.clients.domain.RaiderIoData", data["type"]!!.jsonPrimitive.content)
        assertNotNull(data["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(data["score"])
        assertNotNull(data["mythicPlusRanks"])
    }

    @And("the response contains an operation")
    fun responseContainsOperation() {
        val body = runBlocking { world.response.bodyAsText() }
        val operation = Json.parseToJsonElement(body).jsonObject["operation"]
        assertNotNull(operation?.takeIf { it.toString() != "null" })
    }

    @And("the operation is null")
    fun operationIsNull() {
        val body = runBlocking { world.response.bodyAsText() }
        val operation = Json.parseToJsonElement(body).jsonObject["operation"]
        assertNull(operation?.takeIf { it.toString() != "null" })
    }
}
