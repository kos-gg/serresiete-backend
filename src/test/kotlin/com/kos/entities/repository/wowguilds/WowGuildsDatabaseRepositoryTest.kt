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

    private suspend fun createView(owner: String): String {
        val id = UUID.randomUUID().toString()
        viewsRepository.create(id, "view-$id", owner, listOf(), Game.WOW_HC, false, null)
        return id
    }

    @Test
    fun `redelivering the same guild insert for the same view succeeds as a no-op`() {
        runBlocking {
            val viewId = createView("sanxei")

            val first = repository.insertGuild(1L, "guild", "realm", "region", viewId)
            assertEquals(Either.Right(Unit), first)

            // simulates redelivery of the same event after a prior attempt already inserted the guild
            val second = repository.insertGuild(1L, "guild", "realm", "region", viewId)
            assertEquals(Either.Right(Unit), second)
        }
    }

    @Test
    fun `inserting a guild already tracked by a different view still fails`() {
        runBlocking {
            val viewA = createView("sanxei")
            val viewB = createView("sanxei")

            val first = repository.insertGuild(1L, "guild", "realm", "region", viewA)
            assertEquals(Either.Right(Unit), first)

            val second = repository.insertGuild(1L, "guild", "realm", "region", viewB)
            assertIs<Either.Left<*>>(second)
        }
    }
}
