package acceptance.steps

import acceptance.SharedInfrastructure
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking

class SyncSteps {

    @When("the WOW sync subscription processes pending events")
    fun syncWowProcessesPendingEvents() {
        runBlocking { SharedInfrastructure.subscriptions.syncWow.processPendingEvents() }
    }
}
