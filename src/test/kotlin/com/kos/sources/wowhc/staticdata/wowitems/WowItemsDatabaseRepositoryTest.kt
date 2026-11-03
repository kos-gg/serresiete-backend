package com.kos.sources.wowhc.staticdata.wowitems

import com.kos.clients.domain.AssetKeyValue
import com.kos.clients.domain.GetWowMediaResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemResponse
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowItemsDatabaseRepositoryTest {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    private val repository: WowItemsDatabaseRepository =
        WowItemsDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

    @BeforeEach
    fun beforeEach() {
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun afterAll() {
        embeddedPostgres.close()
    }

    @Test
    fun `i can get item media from database`() = runBlocking {
        givenAWowClassicItemInDatabase()

        assertEquals("1.jpg", repository.getItemMedia(1))
    }


    @Test
    fun `i can get item from database`() {
        runBlocking {
            givenAWowClassicItemInDatabase()
            assertEquals("Backwood Helm", repository.getItem(1)?.name)
        }
    }

    @Test
    fun `an item that is not stored is not found`() = runBlocking {
        assertNull(repository.getItem(1))
        assertNull(repository.getItemMedia(1))
    }

    private suspend fun givenAWowClassicItemInDatabase() {
        repository.withState(
            WowItemsState(
                listOf(
                    WowItemState(
                        id = 1,
                        media = Json.encodeToString(GetWowMediaResponse(listOf(AssetKeyValue("icon", "1.jpg")))),
                        item = Json.encodeToString(getWowItemResponse)
                    )
                )
            )
        )
    }
}