package acceptance.steps

import acceptance.JwtHelper
import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.roles.Role
import com.kos.roles.repository.RolesActivitiesDatabaseRepository
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mindrot.jbcrypt.BCrypt
import java.util.*
import kotlin.test.assertNotNull

class AuthSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client
    private val jwtConfig = SharedInfrastructure.jwtConfig
    private val db = SharedInfrastructure.db

    @Given("{string} has a valid token with activities {string}")
    fun userHasValidTokenWithActivities(username: String, activities: String) {
        scenarioVariables.token =
            JwtHelper.validJwt(jwtConfig, username, *activities.split(",").map { it.trim() }.toTypedArray())
    }

    @Given("{string} is registered with password {string} and role {string}")
    fun userIsRegisteredWithPassword(username: String, password: String, roleName: String) {
        val role = Role.valueOf(roleName.uppercase())
        val credentialsRepo = CredentialsDatabaseRepository(db)
        runBlocking {
            credentialsRepo.insertCredentials(username, BCrypt.hashpw(password, BCrypt.gensalt(12)))
            credentialsRepo.insertRoles(username, setOf(role))
        }
    }

    @Given("role {string} has activities {string}")
    fun roleHasActivities(roleName: String, activities: String) {
        val role = Role.valueOf(roleName.uppercase())
        val rolesActivitiesRepo = RolesActivitiesDatabaseRepository(db)
        runBlocking {
            rolesActivitiesRepo.updateActivitiesFromRole(role, activities.split(",").map { it.trim() }.toSet())
        }
    }

    @When("they login with username {string} and password {string}")
    fun theyLogin(username: String, password: String) {
        scenarioVariables.response = runBlocking {
            client.post("/api/auth") {
                val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                header(HttpHeaders.Authorization, "Basic $credentials")
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            val json = Json.parseToJsonElement(body).jsonObject
            scenarioVariables.token = json["accessToken"]?.jsonPrimitive?.contentOrNull
            scenarioVariables.refreshToken = json["refreshToken"]?.jsonPrimitive?.contentOrNull
        }
    }

    @Then("the response contains an access token and a refresh token")
    fun responseContainsAccessAndRefreshTokens() {
        assertNotNull(scenarioVariables.token, "Expected accessToken in login response")
        assertNotNull(scenarioVariables.refreshToken, "Expected refreshToken in login response")
    }

    @When("they refresh their access token")
    fun theyRefreshToken() {
        scenarioVariables.response = runBlocking {
            client.post("/api/auth/refresh") {
                scenarioVariables.refreshToken?.let { bearerAuth(it) }
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            val json = Json.parseToJsonElement(body).jsonObject
            scenarioVariables.token = json["accessToken"]?.jsonPrimitive?.contentOrNull
        }
    }

    @Then("the response contains a new access token")
    fun responseContainsNewAccessToken() {
        assertNotNull(scenarioVariables.token, "Expected a new accessToken in refresh response")
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

}
