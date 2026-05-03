package acceptance.steps

import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import com.kos.views.Game
import com.kos.views.ViewPatchRequest
import com.kos.views.ViewRequest
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

class ViewsSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client
    private val db = SharedInfrastructure.db

    @When("they create a {string} view")
    fun createView(game: String) {
        val resolvedGame = Game.valueOf(game.uppercase())
        scenarioVariables.game = resolvedGame
        scenarioVariables.response = runBlocking {
            client.post("/api/views") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(ViewRequest(name = "My View", published = false, entities = emptyList(), game = resolvedGame, featured = false))
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            scenarioVariables.viewId = Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
        }
    }

    @And("the views subscription processes pending events")
    fun processViewsSubscription() {
        runBlocking { SharedInfrastructure.subscriptions.views.processPendingEvents() }
    }

    @When("they GET the created view")
    fun getCreatedView() {
        scenarioVariables.response = runBlocking {
            client.get("/api/views/${scenarioVariables.viewId}") {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @When("they edit the created view to be named {string}")
    fun editCreatedView(name: String) {
        scenarioVariables.response = runBlocking {
            client.put("/api/views/${scenarioVariables.viewId}") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(ViewRequest(name = name, published = false, entities = emptyList(), game = scenarioVariables.game!!, featured = false))
            }
        }
    }

    @When("they edit a view at {string} to be named {string}")
    fun editViewAtPath(path: String, name: String) {
        scenarioVariables.response = runBlocking {
            client.put(path) {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(ViewRequest(name = name, published = false, entities = emptyList(), game = Game.LOL, featured = false))
            }
        }
    }

    @When("they patch the created view to be named {string}")
    fun patchCreatedView(name: String) {
        scenarioVariables.response = runBlocking {
            client.patch("/api/views/${scenarioVariables.viewId}") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(ViewPatchRequest(name = name, game = scenarioVariables.game!!))
            }
        }
    }

    @When("they DELETE the created view")
    fun deleteCreatedView() {
        scenarioVariables.response = runBlocking {
            client.delete("/api/views/${scenarioVariables.viewId}") {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @When("they request DELETE {string}")
    fun requestDelete(path: String) {
        scenarioVariables.response = runBlocking {
            client.delete(path) {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @Then("GET {string} returns {int} view(s)")
    fun getViewsReturnsCount(path: String, expectedCount: Int) {
        val response = runBlocking {
            client.get(path) {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = runBlocking { response.bodyAsText() }
        val records = Json.parseToJsonElement(body).jsonObject["records"]!!.jsonArray
        assertEquals(expectedCount, records.size)
    }
}
