package acceptance.steps

import acceptance.MockConfig
import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import arrow.core.getOrElse
import com.kos.auth.Authorization
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntity
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.wowguilds.WowGuildsDatabaseRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonDatabaseRepository
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksDatabaseRepository
import com.kos.views.Game
import com.kos.views.SimpleView
import com.kos.views.ViewEntity
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

    private val wowGuildViewId = "test-view-id"
    private val wowGuildViewId2 = "test-view-id-2"
    private val wowGuildName = "Method"
    private val wowGuildRealm = "Twisting-Nether"
    private val wowGuildRegion = "eu"
    private val wowGuildExistingMemberName = "Sanxei"
    private val wowGuildNewMemberName = "Newmember"

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
            guildsRepo.insertGuild(12345, guildName, realm, region, viewId, Game.WOW_HC)
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

    @And("a WOW guild view exists in the repository with entities associated")
    fun wowGuildViewExistsWithEntitiesAssociated() {
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val existingEntity = runBlocking {
            entitiesRepo.insert(
                listOf(
                    WowEntityRequest(wowGuildExistingMemberName, wowGuildRegion, wowGuildRealm),
                    WowEntityRequest("Kakarona", wowGuildRegion, wowGuildRealm)
                ), Game.WOW
            )
        }.getOrNull()!!.first()

        val viewsRepo = ViewsDatabaseRepository(db)
        val guildsRepo = WowGuildsDatabaseRepository(db)
        runBlocking {
            viewsRepo.withState(
                ViewsState(
                    views = listOf(
                        SimpleView(
                            wowGuildViewId, "Test View", "sanxei", false, listOf(existingEntity.id), Game.WOW, false
                        )
                    ),
                    viewEntities = listOf(ViewEntity(existingEntity.id, wowGuildViewId, null))
                )
            )
            guildsRepo.insertGuild(12345, wowGuildName, wowGuildRealm, wowGuildRegion, wowGuildViewId, Game.WOW)
        }
    }

    @And("two WOW guild views exist in the repository tracking the same guild")
    fun twoWowGuildViewsExistTrackingTheSameGuild() {
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val existingEntity = runBlocking {
            entitiesRepo.insert(
                listOf(
                    WowEntityRequest(wowGuildExistingMemberName, wowGuildRegion, wowGuildRealm),
                    WowEntityRequest("Kakarona", wowGuildRegion, wowGuildRealm)
                ), Game.WOW
            )
        }.getOrNull()!!.first()

        val viewsRepo = ViewsDatabaseRepository(db)
        val guildsRepo = WowGuildsDatabaseRepository(db)
        runBlocking {
            viewsRepo.withState(
                ViewsState(
                    views = listOf(
                        SimpleView(
                            wowGuildViewId, "Test View A", "sanxei", false, listOf(existingEntity.id), Game.WOW, false
                        ),
                        SimpleView(
                            wowGuildViewId2, "Test View B", "sanxei", false, listOf(existingEntity.id), Game.WOW, false
                        )
                    ),
                    viewEntities = listOf(
                        ViewEntity(existingEntity.id, wowGuildViewId, null),
                        ViewEntity(existingEntity.id, wowGuildViewId2, null)
                    )
                )
            )
            guildsRepo.insertGuild(12345, wowGuildName, wowGuildRealm, wowGuildRegion, wowGuildViewId, Game.WOW)
            guildsRepo.insertGuild(12345, wowGuildName, wowGuildRealm, wowGuildRegion, wowGuildViewId2, Game.WOW)
        }
    }

    @And("the current roster is retrieved from the Blizzard API")
    fun theCurrentRosterIsRetrievedFromBlizzard() {
        MockConfig.wowGuildRosterMembers = listOf(
            wowGuildExistingMemberName to 90,
            wowGuildNewMemberName to 90
        )
    }

    @When("the WOW guild updater is processed")
    fun theWowGuildUpdaterIsProcessed() {
        scenarioVariables.response = runBlocking {
            client.post("/api/tasks") {
                scenarioVariables.token?.let { bearerAuth(it) }
                contentType(ContentType.Application.Json)
                setBody("""{"type":"${TaskType.UPDATE_WOW_GUILDS.name}"}""")
            }
        }
    }

    @Then("the associated view is updated with the current roster")
    fun theAssociatedViewIsUpdatedWithCurrentRoster() {
        val viewsRepo = ViewsDatabaseRepository(db)
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val expectedNames = setOf(wowGuildExistingMemberName.lowercase(), wowGuildNewMemberName.lowercase())
        runBlocking {
            assertUntil(5, 1000) {
                val view = viewsRepo.get(wowGuildViewId)!!
                val entityNames = entitiesRepo.get(Game.WOW)
                    .filter { it.id in view.entitiesIds }
                    .map { it.name }
                    .toSet()
                assertEquals(expectedNames, entityNames)
            }
        }
    }

    @Then("both associated views are updated with the current roster")
    fun bothAssociatedViewsAreUpdatedWithCurrentRoster() {
        val viewsRepo = ViewsDatabaseRepository(db)
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val expectedNames = setOf(wowGuildExistingMemberName.lowercase(), wowGuildNewMemberName.lowercase())
        runBlocking {
            assertUntil(5, 1000) {
                listOf(wowGuildViewId, wowGuildViewId2).forEach { viewId ->
                    val view = viewsRepo.get(viewId)!!
                    val entityNames = entitiesRepo.get(Game.WOW)
                        .filter { it.id in view.entitiesIds }
                        .map { it.name }
                        .toSet()
                    assertEquals(expectedNames, entityNames)
                }
            }
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
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val entity = runBlocking {
            entitiesRepo.insert(
                listOf(
                    LolEnrichedEntityRequest(
                        "GTP ZeroMVPs",
                        "EUW",
                        "test-puuid-GTP-ZeroMVPs",
                        0,
                        0
                    )
                ), Game.LOL
            )
        }.getOrNull()!!.first()

        val viewsRepo = ViewsDatabaseRepository(db)
        runBlocking {
            viewsRepo.withState(
                ViewsState(
                    views = listOf(
                        SimpleView(
                            viewId,
                            "Test LOL View",
                            "sanxei",
                            false,
                            listOf(entity.id),
                            Game.LOL,
                            false
                        )
                    ),
                    viewEntities = listOf(ViewEntity(entity.id, viewId, null))
                )
            )
        }
    }

    @Then("the LOL entity has been renamed to {string} {string}")
    fun lolEntityHasBeenRenamed(newName: String, newTag: String) {
        val entitiesRepo = EntitiesDatabaseRepository(db)
        runBlocking {
            val entities = entitiesRepo.get(Game.LOL)
            assertTrue(
                entities.any { (it as? LolEntity)?.let { e -> e.name == newName && e.tag == newTag } == true },
                "Expected a LOL entity renamed to $newName#$newTag but found: $entities"
            )
        }
    }

    @Then("a new WOW season {string} has been added")
    fun newWowSeasonHasBeenAdded(seasonName: String) {
        val seasonRepo = WowSeasonDatabaseRepository(db)
        runBlocking {
            val seasons = seasonRepo.state().wowSeasons
            assertTrue(
                seasons.any { it.name == seasonName },
                "Expected a WOW season named \"$seasonName\" to have been added but found: ${seasons.map { it.name }}"
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
