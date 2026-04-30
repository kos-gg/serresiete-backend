package acceptance.steps

import acceptance.JwtHelper
import acceptance.SharedInfrastructure
import acceptance.World
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

class AuthSteps(private val world: World) {

    private val client = SharedInfrastructure.client
    private val jwtConfig = SharedInfrastructure.jwtConfig

    @Given("{string} has a valid token with activity {string}")
    fun userHasValidToken(username: String, activity: String) {
        world.token = JwtHelper.validJwt(jwtConfig, username, activity)
    }

    @Given("the request has no authentication")
    fun noAuthentication() {
        world.token = null
    }

    @Given("{string} has an expired token")
    fun userHasExpiredToken(username: String) {
        world.token = JwtHelper.expiredJwt(jwtConfig, username)
    }

    @Given("{string} has a refresh token")
    fun userHasRefreshToken(username: String) {
        world.token = JwtHelper.refreshJwt(jwtConfig, username)
    }

    @When("they request GET {string}")
    fun requestGet(path: String) {
        world.response = runBlocking {
            client.get(path) {
                world.token?.let { bearerAuth(it) }
            }
        }
    }

    @Then("the response status is {int}")
    fun responseStatusIs(status: Int) {
        assertEquals(status, world.response.status.value)
    }
}
