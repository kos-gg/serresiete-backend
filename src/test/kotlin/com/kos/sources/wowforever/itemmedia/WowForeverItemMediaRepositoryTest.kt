package com.kos.sources.wowforever.itemmedia

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class WowForeverItemMediaRepositoryTestCommon {

    abstract val repository: WowForeverItemMediaRepository

    private val shirt = WowForeverItemMedia(6125, "https://render.worldofwarcraft.com/forever-eu/icons/56/shirt.jpg")
    private val chest = WowForeverItemMedia(15487, "https://render.worldofwarcraft.com/forever-eu/icons/56/chest.jpg")

    @Test
    fun `given an empty repository i can insert item icons`() {
        runBlocking {
            repository.insert(listOf(shirt, chest))
            assertEquals(listOf(shirt, chest), repository.state())
        }
    }

    @Test
    fun `getIcons returns only the requested items that are stored`() {
        runBlocking {
            repository.withState(listOf(shirt, chest))
            assertEquals(mapOf(shirt.itemId to shirt.icon), repository.getIcons(setOf(shirt.itemId, 999)))
        }
    }

    @Test
    fun `getIcons of an empty set returns an empty map`() {
        runBlocking {
            repository.withState(listOf(shirt))
            assertEquals(mapOf(), repository.getIcons(setOf()))
        }
    }

    @Test
    fun `inserting an already stored item keeps the first icon`() {
        runBlocking {
            repository.withState(listOf(shirt))
            repository.insert(listOf(shirt.copy(icon = "other.jpg"), chest))
            assertEquals(listOf(shirt, chest), repository.state())
        }
    }
}

class WowForeverItemMediaInMemoryRepositoryTest : WowForeverItemMediaRepositoryTestCommon() {
    override val repository = WowForeverItemMediaInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowForeverItemMediaDatabaseRepositoryTest : WowForeverItemMediaRepositoryTestCommon() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = WowForeverItemMediaDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
