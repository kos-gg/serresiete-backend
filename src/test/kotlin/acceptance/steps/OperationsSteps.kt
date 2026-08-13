package acceptance.steps

import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

class OperationsSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client

    @When("they GET the operation status")
    fun getOperationStatus() {
        val operationId = requireNotNull(scenarioVariables.operationId) { "No operationId in scenario" }
        scenarioVariables.response = runBlocking {
            client.get("/api/operations/$operationId") {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @Then("the operation status is {string}")
    fun operationStatusIs(status: String) {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals(status, json["status"]!!.jsonPrimitive.content)
    }
}
