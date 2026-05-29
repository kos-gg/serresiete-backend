package acceptance.steps

import acceptance.JwtHelper
import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client
    private val jwtConfig = SharedInfrastructure.jwtConfig

    @Given("{string} has a valid token with activities {string}")
    fun userHasValidTokenWithActivities(username: String, activities: String) {
        scenarioVariables.token = JwtHelper.validJwt(jwtConfig, username, *activities.split(",").map { it.trim() }.toTypedArray())
    }

    @Given("the request has no authentication")
    fun noAuthentication() {
        scenarioVariables.token = null
    }

    @Given("{string} has an expired token")
    fun userHasExpiredToken(username: String) {
        scenarioVariables.token = JwtHelper.expiredJwt(jwtConfig, username)
    }

    @Given("{string} has a refresh token")
    fun userHasRefreshToken(username: String) {
        scenarioVariables.token = JwtHelper.refreshJwt(jwtConfig, username)
    }

    @When("they request GET {string}")
    fun requestGet(path: String) {
        scenarioVariables.response = runBlocking {
            client.get(path) {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @When("they login as {string} with password {string}")
    fun loginAs(username: String, password: String) {
        scenarioVariables.response = runBlocking {
            client.post("/api/auth") {
                basicAuth(username, password)
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            val json = Json.parseToJsonElement(body).jsonObject
            scenarioVariables.token = json["accessToken"]?.jsonPrimitive?.content
            scenarioVariables.refreshToken = scenarioVariables.response.setCookie()
                .find { it.name == "refreshToken" }?.value
                ?: json["refreshToken"]?.jsonPrimitive?.content
        }
    }

    @Given("they have logged in as {string} with password {string}")
    fun haveLoggedIn(username: String, password: String) = loginAs(username, password)

    @When("they refresh using the cookie")
    fun refreshUsingCookie() {
        scenarioVariables.response = runBlocking {
            client.post("/api/auth/refresh") {
                scenarioVariables.refreshToken?.let { header("Cookie", "refreshToken=$it") }
            }
        }
    }

    @When("they refresh using the Bearer header")
    fun refreshUsingBearerHeader() {
        scenarioVariables.response = runBlocking {
            client.post("/api/auth/refresh") {
                scenarioVariables.refreshToken?.let { bearerAuth(it) }
            }
        }
    }

    @When("they logout")
    fun logout() {
        scenarioVariables.response = runBlocking {
            client.delete("/api/auth") {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @When("they refresh without credentials")
    fun refreshWithoutCredentials() {
        scenarioVariables.response = runBlocking {
            client.post("/api/auth/refresh")
        }
    }

    @Then("the response has an httpOnly refreshToken cookie")
    fun responseHasHttpOnlyRefreshTokenCookie() {
        val cookie = scenarioVariables.response.setCookie().find { it.name == "refreshToken" }
        assertNotNull(cookie, "Expected Set-Cookie: refreshToken but none found")
        assertTrue(cookie.httpOnly, "Expected refreshToken cookie to be httpOnly")
        assertTrue(cookie.value.isNotBlank(), "Expected refreshToken cookie value to be non-blank")
    }

    @Then("the response body contains an access token")
    fun responseBodyContainsAccessToken() {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val accessToken = Json.parseToJsonElement(body).jsonObject["accessToken"]?.jsonPrimitive?.content
        assertTrue(accessToken?.isNotBlank() == true, "Expected non-blank accessToken in response body")
    }

    @Then("the response clears the refreshToken cookie")
    fun responseClearsRefreshTokenCookie() {
        val header = scenarioVariables.response.headers.getAll(HttpHeaders.SetCookie)
            ?.find { it.startsWith("refreshToken=") }
        assertNotNull(header, "Expected Set-Cookie: refreshToken but none found")
        assertTrue(header.contains("Max-Age=0"), "Expected Max-Age=0 in Set-Cookie header but got: $header")
    }

}
