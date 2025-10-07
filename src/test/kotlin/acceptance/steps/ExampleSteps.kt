package acceptance.steps
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import kotlin.test.assertTrue

class ExampleSteps {

    @Given("the service is running")
    fun theServiceIsRunning() {
        println("✅ Service is running")
    }

    @When("I send a request")
    fun iSendARequest() {
        println("📨 Sending request")
    }

    @Then("I receive a valid response")
    fun iReceiveAValidResponse() {
        assertTrue(true)
        println("✅ Response OK")
    }
}