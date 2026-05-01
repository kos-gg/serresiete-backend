package acceptance.steps

import acceptance.SharedInfrastructure
import acceptance.World
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.roles.Role
import com.kos.views.Game
import com.kos.views.ViewPatchRequest
import com.kos.views.ViewRequest
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
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

class ViewsSteps(private val world: World) {

    private val client = SharedInfrastructure.client
    private val db = SharedInfrastructure.db

    @Given("{string} exists in the database with role {string}")
    fun userExistsInDatabase(username: String, roleName: String) {
        val role = Role.valueOf(roleName.uppercase())
        val repo = CredentialsDatabaseRepository(db)
        runBlocking {
            repo.insertCredentials(username, "test-password")
            repo.insertRoles(username, setOf(role))
        }
    }

    @When("they create a {string} view named {string}")
    fun createView(game: String, name: String) {
        val resolvedGame = Game.valueOf(game.uppercase())
        world.game = resolvedGame
        world.response = runBlocking {
            client.post("/api/views") {
                contentType(ContentType.Application.Json)
                world.token?.let { bearerAuth(it) }
                setBody(ViewRequest(name = name, published = false, entities = emptyList(), game = resolvedGame, featured = false))
            }
        }
        if (world.response.status == HttpStatusCode.OK) {
            val body = runBlocking { world.response.bodyAsText() }
            world.viewId = Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
        }
    }

    @And("the views subscription processes pending events")
    fun processViewsSubscription() {
        runBlocking { SharedInfrastructure.subscriptions.views.processPendingEvents() }
    }

    @When("they GET the created view")
    fun getCreatedView() {
        world.response = runBlocking {
            client.get("/api/views/${world.viewId}") {
                world.token?.let { bearerAuth(it) }
            }
        }
    }

    @When("they edit the created view to be named {string}")
    fun editCreatedView(name: String) {
        world.response = runBlocking {
            client.put("/api/views/${world.viewId}") {
                contentType(ContentType.Application.Json)
                world.token?.let { bearerAuth(it) }
                setBody(ViewRequest(name = name, published = false, entities = emptyList(), game = world.game!!, featured = false))
            }
        }
    }

    @When("they edit a view at {string} to be named {string}")
    fun editViewAtPath(path: String, name: String) {
        world.response = runBlocking {
            client.put(path) {
                contentType(ContentType.Application.Json)
                world.token?.let { bearerAuth(it) }
                setBody(ViewRequest(name = name, published = false, entities = emptyList(), game = Game.LOL, featured = false))
            }
        }
    }

    @When("they patch the created view to be named {string}")
    fun patchCreatedView(name: String) {
        world.response = runBlocking {
            client.patch("/api/views/${world.viewId}") {
                contentType(ContentType.Application.Json)
                world.token?.let { bearerAuth(it) }
                setBody(ViewPatchRequest(name = name, game = world.game!!))
            }
        }
    }

    @When("they DELETE the created view")
    fun deleteCreatedView() {
        world.response = runBlocking {
            client.delete("/api/views/${world.viewId}") {
                world.token?.let { bearerAuth(it) }
            }
        }
    }

    @When("they request DELETE {string}")
    fun requestDelete(path: String) {
        world.response = runBlocking {
            client.delete(path) {
                world.token?.let { bearerAuth(it) }
            }
        }
    }

    @Then("GET {string} returns {int} view(s)")
    fun getViewsReturnsCount(path: String, expectedCount: Int) {
        val response = runBlocking {
            client.get(path) {
                world.token?.let { bearerAuth(it) }
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = runBlocking { response.bodyAsText() }
        val records = Json.parseToJsonElement(body).jsonObject["records"]!!.jsonArray
        assertEquals(expectedCount, records.size)
    }
}
