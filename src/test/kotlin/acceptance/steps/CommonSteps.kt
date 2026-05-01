package acceptance.steps

import acceptance.SharedInfrastructure
import acceptance.World
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.roles.Role
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

class CommonSteps(private val world: World) {

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
        assertEquals(status, world.response.status.value)
    }
}
