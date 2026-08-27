package com.kos.entities.repository.wowguilds

import arrow.core.Either
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
import kotlin.test.assertIs

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

            val first = repository.insertGuild(1L, "guild", "realm", "region", viewId, Game.WOW)
            assertEquals(Either.Right(Unit), first)

            // simulates redelivery of the same event after a prior attempt already inserted the guild
            val second = repository.insertGuild(1L, "guild", "realm", "region", viewId, Game.WOW)
            assertEquals(Either.Right(Unit), second)
        }
    }

    @Test
    fun `inserting a guild already tracked by a different view still fails`() {
        runBlocking {
            val viewA = createView("sanxei", Game.WOW)
            val viewB = createView("sanxei", Game.WOW)

            val first = repository.insertGuild(1L, "guild", "realm", "region", viewA, Game.WOW)
            assertEquals(Either.Right(Unit), first)

            val second = repository.insertGuild(1L, "guild", "realm", "region", viewB, Game.WOW)
            assertIs<Either.Left<*>>(second)
        }
    }

    @Test
    fun `the same blizzard guild id can be tracked independently for wow and wow_hc`() {
        runBlocking {
            val wowView = createView("sanxei", Game.WOW)
            val wowHcView = createView("sanxei", Game.WOW_HC)

            val wowInsert = repository.insertGuild(1L, "guild", "realm", "region", wowView, Game.WOW)
            val wowHcInsert = repository.insertGuild(1L, "guild", "realm", "region", wowHcView, Game.WOW_HC)

            assertEquals(Either.Right(Unit), wowInsert)
            assertEquals(Either.Right(Unit), wowHcInsert)
        }
    }

    @Test
    fun `getGuilds only returns guilds tracked for the requested game`() {
        runBlocking {
            val wowView = createView("sanxei", Game.WOW)
            val wowHcView = createView("sanxei", Game.WOW_HC)

            repository.insertGuild(1L, "wow-guild", "realm", "region", wowView, Game.WOW)
            repository.insertGuild(2L, "hc-guild", "realm", "region", wowHcView, Game.WOW_HC)

            val wowGuilds = repository.getGuilds(Game.WOW)

            assertEquals(1, wowGuilds.size)
            assertEquals("wow-guild", wowGuilds.single().first.name)
        }
    }
}
