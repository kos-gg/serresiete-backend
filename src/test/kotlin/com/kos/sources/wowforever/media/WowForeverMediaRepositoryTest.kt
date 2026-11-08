package com.kos.sources.wowforever.media

import com.kos.sources.wowforever.media.WowForeverMediaKind.ITEM
import com.kos.sources.wowforever.media.WowForeverMediaKind.SPELL
import com.kos.sources.wowforever.media.WowForeverMediaStatus.PENDING
import com.kos.sources.wowforever.media.WowForeverMediaStatus.RESOLVED
import com.kos.sources.wowforever.media.WowForeverMediaStatus.UNAVAILABLE
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

    private val resolvedBoots = WowForeverMedia(ITEM, 6350, "eu", "boots.jpg", RESOLVED, 0)
    private val pendingBelt = WowForeverMedia(ITEM, 2857, "eu", null, PENDING, 0)
    private val unavailableRing = WowForeverMedia(ITEM, 281268, "eu", null, UNAVAILABLE, 0)
    private val resolvedSpell = WowForeverMedia(SPELL, 6350, "eu", "spell.jpg", RESOLVED, 0)

    @Test
    fun `getIcons returns only resolved icons of the requested kind and ids`() {
        runBlocking {
            repository.withState(listOf(resolvedBoots, pendingBelt, unavailableRing, resolvedSpell))
            assertEquals(
                mapOf(resolvedBoots.id to "boots.jpg"),
                repository.getIcons(ITEM, setOf(resolvedBoots.id, pendingBelt.id, unavailableRing.id, 999))
            )
        }
    }

    @Test
    fun `getIcons of an empty set returns an empty map`() {
        runBlocking {
            repository.withState(listOf(resolvedBoots))
            assertEquals(mapOf(), repository.getIcons(ITEM, setOf()))
        }
    }

    @Test
    fun `registerPending stores new ids as pending and ignores already known ones`() {
        runBlocking {
            repository.withState(listOf(resolvedBoots, unavailableRing))
            repository.registerPending(ITEM, "us", setOf(resolvedBoots.id, unavailableRing.id, pendingBelt.id))
            assertEquals(
                listOf(pendingBelt.copy(region = "us"), resolvedBoots, unavailableRing),
                repository.state()
            )
        }
    }

    @Test
    fun `the same id of a different kind is a different media`() {
        runBlocking {
            repository.withState(listOf(resolvedBoots))
            repository.registerPending(SPELL, "eu", setOf(resolvedBoots.id))
            assertEquals(
                listOf(resolvedBoots, WowForeverMedia(SPELL, resolvedBoots.id, "eu", null, PENDING, 0)),
                repository.state()
            )
        }
    }

    @Test
    fun `pending returns only pending media up to the limit`() {
        runBlocking {
            val pendingSpell = WowForeverMedia(SPELL, 12282, "eu", null, PENDING, 0)
            repository.withState(listOf(resolvedBoots, pendingBelt, unavailableRing, pendingSpell))
            assertEquals(
                listOf(WowForeverPendingMedia(ITEM, pendingBelt.id, "eu"), WowForeverPendingMedia(SPELL, 12282, "eu")),
                repository.pending(10)
            )
            assertEquals(listOf(WowForeverPendingMedia(ITEM, pendingBelt.id, "eu")), repository.pending(1))
        }
    }

    @Test
    fun `resolve stores the icon of a pending media`() {
        runBlocking {
            repository.withState(listOf(pendingBelt))
            repository.resolve(ITEM, pendingBelt.id, "belt.jpg")
            assertEquals(listOf(pendingBelt.copy(icon = "belt.jpg", status = RESOLVED)), repository.state())
        }
    }

    @Test
    fun `resolve never overrides a media that is not pending`() {
        runBlocking {
            repository.withState(listOf(resolvedBoots, unavailableRing))
            repository.resolve(ITEM, resolvedBoots.id, "other.jpg")
            repository.resolve(ITEM, unavailableRing.id, "ring.jpg")
            assertEquals(listOf(resolvedBoots, unavailableRing), repository.state())
        }
    }

    @Test
    fun `markUnavailable stops a pending media from being pending`() {
        runBlocking {
            repository.withState(listOf(pendingBelt))
            repository.markUnavailable(ITEM, pendingBelt.id)
            assertEquals(listOf(pendingBelt.copy(status = UNAVAILABLE)), repository.state())
        }
    }

    @Test
    fun `markFailed keeps the media pending until it reaches the max attempts`() {
        runBlocking {
            repository.withState(listOf(pendingBelt))
            repository.markFailed(ITEM, pendingBelt.id, maxAttempts = 2)
            assertEquals(listOf(pendingBelt.copy(attempts = 1)), repository.state())
            repository.markFailed(ITEM, pendingBelt.id, maxAttempts = 2)
            assertEquals(listOf(pendingBelt.copy(attempts = 2, status = UNAVAILABLE)), repository.state())
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
