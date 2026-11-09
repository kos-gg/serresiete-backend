package com.kos.sources.wowforever.media

import com.kos.sources.wowforever.media.WowForeverMediaKind.ITEM
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class WowForeverMediaRepositoryTestCommon {

    abstract val repository: WowForeverMediaRepository

    private val boots = WowForeverMedia(ITEM, 6350, "boots.jpg")
    private val belt = WowForeverMedia(ITEM, 2857, "belt.jpg")

    @Test
    fun `given an empty repository i can insert icons`() {
        runBlocking {
            repository.insert(listOf(boots, belt))
            assertEquals(listOf(belt, boots), repository.state())
        }
    }

    @Test
    fun `getIcons returns only the requested ids that are stored`() {
        runBlocking {
            repository.withState(listOf(boots, belt))
            assertEquals(mapOf(boots.id to "boots.jpg"), repository.getIcons(ITEM, setOf(boots.id, 999)))
        }
    }

    @Test
    fun `getIcons of an empty set returns an empty map`() {
        runBlocking {
            repository.withState(listOf(boots))
            assertEquals(mapOf(), repository.getIcons(ITEM, setOf()))
        }
    }

    @Test
    fun `inserting an already stored icon keeps the first one`() {
        runBlocking {
            repository.withState(listOf(boots))
            repository.insert(listOf(boots.copy(icon = "other.jpg"), belt))
            assertEquals(listOf(belt, boots), repository.state())
        }
    }
}

class WowForeverMediaInMemoryRepositoryTest : WowForeverMediaRepositoryTestCommon() {
    override val repository = WowForeverMediaInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowForeverMediaDatabaseRepositoryTest : WowForeverMediaRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = WowForeverMediaDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

    @BeforeEach
    fun beforeEach() {
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun afterAll() {
        embeddedPostgres.close()
    }
}
