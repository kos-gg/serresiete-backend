package com.kos.entities.repository.wowguilds

import com.kos.entities.domain.WowForeverGuild
import com.kos.entities.domain.WowRealmGuild
import com.kos.views.Game
import com.kos.views.repository.ViewsDatabaseRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowGuildsDatabaseRepositoryTest {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    private val db = Database.connect(embeddedPostgres.postgresDatabase)
    private val viewsRepository = ViewsDatabaseRepository(db)
    private val repository = WowGuildsDatabaseRepository(db)

    @BeforeEach
    fun beforeEach() {
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun afterAll() {
        embeddedPostgres.close()
    }

    private suspend fun createView(owner: String, game: Game): String {
        val id = UUID.randomUUID().toString()
        viewsRepository.create(id, "view-$id", owner, listOf(), game, false, null)
        return id
    }

    @Test
    fun `redelivering the same guild insert for the same view succeeds as a no-op`() {
        runBlocking {
            val viewId = createView("sanxei", Game.WOW)

            repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), viewId, Game.WOW)
            repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), viewId, Game.WOW)

            assertEquals(listOf(viewId), repository.getRealmGuilds(Game.WOW).map { it.second })
        }
    }

    @Test
    fun `inserting the same guild for a second view succeeds and both views are tracked`() {
        runBlocking {
            val viewA = createView("sanxei", Game.WOW)
            val viewB = createView("sanxei", Game.WOW)

            val first = repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), viewA, Game.WOW)
            assertEquals(Unit, first)

            val second = repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), viewB, Game.WOW)
            assertEquals(Unit, second)

            val trackedViewIds = repository.getRealmGuilds(Game.WOW).map { it.second }.toSet()
            assertEquals(setOf(viewA, viewB), trackedViewIds)
        }
    }

    @Test
    fun `deleting a view that tracks a guild cascades and removes the tracking row, but leaves other views' rows intact`() {
        runBlocking {
            val viewA = createView("sanxei", Game.WOW)
            val viewB = createView("sanxei", Game.WOW)
            repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), viewA, Game.WOW)
            repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), viewB, Game.WOW)

            viewsRepository.delete(viewA)

            val trackedViewIds = repository.getRealmGuilds(Game.WOW).map { it.second }.toSet()
            assertEquals(setOf(viewB), trackedViewIds)
        }
    }

    @Test
    fun `findTrackedGuild returns null when no guild matches`() {
        runBlocking {
            assertNull(repository.findTrackedGuild("guild", "realm", "region", Game.WOW))
        }
    }

    @Test
    fun `findTrackedGuild finds an already-tracked guild case-insensitively`() {
        runBlocking {
            val viewId = createView("sanxei", Game.WOW)
            repository.insertGuild(WowRealmGuild("Method", "Twisting-Nether", "EU", 1L), viewId, Game.WOW)

            val found = repository.findTrackedGuild("method", "twisting-nether", "eu", Game.WOW)

            assertEquals("method" to viewId, found?.let { it.first.name to it.second })
        }
    }

    @Test
    fun `the same blizzard guild id can be tracked independently for wow and wow_hc`() {
        runBlocking {
            val wowView = createView("sanxei", Game.WOW)
            val wowHcView = createView("sanxei", Game.WOW_HC)

            val wowInsert = repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), wowView, Game.WOW)
            val wowHcInsert = repository.insertGuild(WowRealmGuild("guild", "realm", "region", 1L), wowHcView, Game.WOW_HC)

            assertEquals(Unit, wowInsert)
            assertEquals(Unit, wowHcInsert)
        }
    }

    @Test
    fun `getGuilds only returns guilds tracked for the requested game`() {
        runBlocking {
            val wowView = createView("sanxei", Game.WOW)
            val wowHcView = createView("sanxei", Game.WOW_HC)

            repository.insertGuild(WowRealmGuild("wow-guild", "realm", "region", 1L), wowView, Game.WOW)
            repository.insertGuild(WowRealmGuild("hc-guild", "realm", "region", 2L), wowHcView, Game.WOW_HC)

            val wowGuilds = repository.getRealmGuilds(Game.WOW)

            assertEquals(1, wowGuilds.size)
            assertEquals("wow-guild", wowGuilds.single().first.name)
        }
    }

    @Test
    fun `a wow forever guild is stored without realm and found as a wow forever guild`() {
        runBlocking {
            val viewId = createView("sanxei", Game.WOW_FOREVER)
            repository.insertGuild(WowForeverGuild("TSALAB", "EU", 104078L), viewId, Game.WOW_FOREVER)

            assertEquals(
                WowForeverGuild("tsalab", "eu", 104078L) to viewId,
                repository.findTrackedGuild("tsalab", null, "eu", Game.WOW_FOREVER)
            )
            assertEquals(listOf(WowForeverGuild("tsalab", "eu", 104078L) to viewId), repository.getForeverGuilds())
        }
    }

    @Test
    fun `realm guilds and wow forever guilds are listed separately`() {
        runBlocking {
            val retailView = createView("sanxei", Game.WOW)
            val foreverView = createView("sanxei", Game.WOW_FOREVER)
            repository.insertGuild(WowRealmGuild("method", "twisting-nether", "eu", 1L), retailView, Game.WOW)
            repository.insertGuild(WowForeverGuild("tsalab", "eu", 2L), foreverView, Game.WOW_FOREVER)

            assertEquals(listOf(WowRealmGuild("method", "twisting-nether", "eu", 1L) to retailView), repository.getRealmGuilds(Game.WOW))
            assertEquals(listOf(WowForeverGuild("tsalab", "eu", 2L) to foreverView), repository.getForeverGuilds())
            assertNull(repository.findTrackedGuild("tsalab", "twisting-nether", "eu", Game.WOW_FOREVER))
        }
    }
}
