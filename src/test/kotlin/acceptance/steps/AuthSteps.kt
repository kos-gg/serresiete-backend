package acceptance.steps

import acceptance.JwtHelper
import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking

class AuthSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client
    private val jwtConfig = SharedInfrastructure.jwtConfig

    @Given("{string} has a valid token with activity {string}")
    fun userHasValidToken(username: String, activity: String) {
        scenarioVariables.token = JwtHelper.validJwt(jwtConfig, username, activity)
    }

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

}
