package acceptance.steps

import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import arrow.core.getOrElse
import com.kos.auth.Authorization
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.wowguilds.WowGuildsDatabaseRepository
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksDatabaseRepository
import com.kos.views.Game
import com.kos.views.SimpleView
import com.kos.views.repository.ViewsDatabaseRepository
import com.kos.views.repository.ViewsState
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasksSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client
    private val db = SharedInfrastructure.db

    @And("an expired token exists for {string} in the database")
    fun expiredTokenExistsForUser(username: String) {
        val authRepo = AuthDatabaseRepository(db)
        runBlocking {
            authRepo.withState(
                listOf(
                    Authorization(
                        userName = username,
                        token = "expired-token-$username",
                        lastUsed = OffsetDateTime.now().minusDays(10),
                        validUntil = OffsetDateTime.now().minusDays(5),
                        isAccess = true
                    )
                )
            )
        }
    }

    @And("an old task record exists in the database")
    fun oldTaskRecordExists() {
        val tasksRepo = TasksDatabaseRepository(db)
        runBlocking {
            tasksRepo.withState(
                listOf(
                    Task(
                        id = "old-task-id",
                        type = TaskType.TOKEN_CLEANUP_TASK,
                        taskStatus = TaskStatus(Status.SUCCESSFUL, null),
                        inserted = OffsetDateTime.now().minusDays(10)
                    )
                )
            )
        }
    }

    @When("they run the {string} task")
    fun theyRunTask(taskType: String) {
        val type = TaskType.fromString(taskType).getOrElse { error("Unknown task type: $taskType") }
        scenarioVariables.response = runBlocking {
            client.post("/api/tasks") {
                scenarioVariables.token?.let { bearerAuth(it) }
                contentType(ContentType.Application.Json)
                setBody("""{"type":"${type.name}"}""")
            }
        }
    }

    @Then("the task completes with status {string}")
    fun taskCompletesWithStatus(expectedStatus: String) {
        val taskId = runBlocking { scenarioVariables.response.body<Map<String, String>>()["id"]!! }

        runBlocking {
            assertUntil(5, 1000) {
                val response = client.get("/api/tasks/$taskId") {
                    scenarioVariables.token?.let { bearerAuth(it) }
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val task = response.body<Task>()
                assertEquals(Status.fromString(expectedStatus.lowercase()), task.taskStatus.status)
            }
        }
    }

    @And("the expired token for {string} has been removed")
    fun expiredTokenHasBeenRemoved(username: String) {
        val authRepo = AuthDatabaseRepository(db)
        runBlocking {
            val tokens = authRepo.state()
            assertTrue(
                tokens.none { it.userName == username },
                "Expected no tokens for $username but found some"
            )
        }
    }

    @Then("the old task record has been removed")
    fun oldTaskRecordHasBeenRemoved() {
        val tasksRepo = TasksDatabaseRepository(db)
        runBlocking {
            val task = tasksRepo.getTask("old-task-id")
            assertNull(task, "Expected old task to be removed but it still exists")
        }
    }

    @And("a WOW_HC guild {string} on realm {string} region {string} is associated with view {string}")
    fun wowHcGuildExistsForView(guildName: String, realm: String, region: String, viewId: String) {
        val viewsRepo = ViewsDatabaseRepository(db)
        val guildsRepo = WowGuildsDatabaseRepository(db)
        runBlocking {
            viewsRepo.withState(
                ViewsState(
                    views = listOf(SimpleView(viewId, "Test View", "sanxei", false, emptyList(), Game.WOW_HC, false)),
                    viewEntities = emptyList()
                )
            )
            guildsRepo.insertGuild(12345, guildName, realm, region, viewId)
        }
    }

    @Then("WOW_HC entities exist for the guild members")
    fun wowHcEntitiesExistForGuildMembers() {
        val entitiesRepo = EntitiesDatabaseRepository(db)
        runBlocking {
            val entities = entitiesRepo.get(Game.WOW_HC)
            assertTrue(entities.isNotEmpty(), "Expected WOW_HC entities to be inserted after guild update")
        }
    }

    @Then("the data cache is empty")
    fun dataCacheIsEmpty() {
        val dataCacheRepo = DataCacheDatabaseRepository(db)
        runBlocking {
            val entries = dataCacheRepo.state()
            assertTrue(entries.isEmpty(), "Expected data cache to be empty but found ${entries.size} entries")
        }
    }

    @When("they run the {string} task with viewId {string}")
    fun theyRunTaskWithViewId(taskType: String, viewId: String) {
        val type = TaskType.fromString(taskType).getOrElse { error("Unknown task type: $taskType") }
        scenarioVariables.response = runBlocking {
            client.post("/api/tasks") {
                scenarioVariables.token?.let { bearerAuth(it) }
                contentType(ContentType.Application.Json)
                setBody("""{"type":"${type.name}","arguments":{"viewId":"$viewId"}}""")
            }
        }
    }

    @And("a LOL view {string} exists")
    fun lolViewExists(viewId: String) {
        val viewsRepo = ViewsDatabaseRepository(db)
        runBlocking {
            viewsRepo.withState(
                ViewsState(
                    views = listOf(SimpleView(viewId, "Test LOL View", "sanxei", false, emptyList(), Game.LOL, false)),
                    viewEntities = emptyList()
                )
            )
        }
    }

    @And("a LOL view {string} was recently synced")
    fun lolViewWasRecentlySynced(viewId: String) {
        val viewsRepo = ViewsDatabaseRepository(db)
        runBlocking {
            viewsRepo.withState(
                ViewsState(
                    views = listOf(
                        SimpleView(
                            viewId, "Test LOL View", "sanxei", false, emptyList(), Game.LOL, false,
                            lastSyncedAt = OffsetDateTime.now().minusSeconds(10)
                        )
                    ),
                    viewEntities = emptyList()
                )
            )
        }
    }

    @Then("the task completes with status {string} and a retryAfter timestamp")
    fun taskCompletesWithStatusAndRetryAfter(expectedStatus: String) {
        val taskId = runBlocking { scenarioVariables.response.body<Map<String, String>>()["id"]!! }

        runBlocking {
            assertUntil(5, 1000) {
                val response = client.get("/api/tasks/$taskId") {
                    scenarioVariables.token?.let { bearerAuth(it) }
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val task = response.body<Task>()
                assertEquals(Status.fromString(expectedStatus.lowercase()), task.taskStatus.status)
                assertTrue(task.taskStatus.retryAfter != null, "Expected retryAfter to be set")
            }
        }
    }
}
