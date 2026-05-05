package acceptance.steps

import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import arrow.core.Either
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.roles.Role
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

suspend fun assertUntil(repetitions: Int, delayMillis: Long, block: suspend () -> Unit) {
    delay(delayMillis)
    Either.catch { block() }
        .onRight { return }
        .onLeft { if (repetitions > 1) assertUntil(repetitions - 1, delayMillis, block) else throw it }
}

class CommonSteps(private val scenarioVariables: ScenarioVariables) {

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

    @Then("the response status is {int}")
    fun responseStatusIs(status: Int) {
        assertEquals(status, scenarioVariables.response.status.value)
    }
}
