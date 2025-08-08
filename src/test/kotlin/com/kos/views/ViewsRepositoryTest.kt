package com.kos.views

import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntity2
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.WowEntity
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.EntitiesState
import com.kos.views.ViewsTestHelper.basicSimpleGameViews
import com.kos.views.ViewsTestHelper.basicSimpleLolView
import com.kos.views.ViewsTestHelper.basicSimpleLolViews
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.ViewsTestHelper.featured
import com.kos.views.ViewsTestHelper.gigaSimpleGameViews
import com.kos.views.ViewsTestHelper.id
import com.kos.views.ViewsTestHelper.name
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.ViewsTestHelper.published
import com.kos.views.repository.ViewsDatabaseRepository
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsRepository
import com.kos.views.repository.ViewsState
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

abstract class ViewsRepositoryTest {
    abstract val repository: ViewsRepository
    abstract val entitiesRepository: EntitiesRepository

    @Test
    fun `given a repository with views i can retrieve the views of a game`() {
        runBlocking {
            val repositoryWithState = repository.withState(ViewsState(basicSimpleGameViews, listOf()))
            assertEquals(basicSimpleLolViews, repositoryWithState.getViews(Game.LOL, featured, null, null).second)
        }
    }

    @Test
    fun `i can get views returns wow featured views`() {
        runBlocking {
            val repositoryWithState = repository.withState(ViewsState(basicSimpleGameViews, listOf()))
            assertEquals(
                listOf(basicSimpleWowView.copy(id = "3", featured = true)),
                repositoryWithState.getViews(Game.WOW, true, null, null).second
            )
        }
    }

    @Test
    fun `i can get views returns only featured views if featured parameter is true`() {
        runBlocking {
            val notFeaturedLolView = basicSimpleLolView.copy(id = "4", featured = false)
            val featuredWowView = basicSimpleWowView.copy(id = "3", featured = true)
            val notFeaturedWowView = basicSimpleWowView.copy(id = "5", featured = false)

            val repositoryWithState =
                repository.withState(
                    ViewsState(
                        listOf(featuredWowView, notFeaturedLolView, notFeaturedWowView),
                        listOf()
                    )
                )
            assertEquals(
                listOf(featuredWowView),
                repositoryWithState.getViews(Game.WOW, true, null, null).second
            )
        }
    }

    @Test
    fun `i can get views returns only one view since the limit is 1`() {
        runBlocking {
            val limit = 1
            val repositoryWithState = repository.withState(ViewsState(gigaSimpleGameViews, listOf()))

            val views = repositoryWithState.getViews(Game.LOL, false, null, limit)

            assertEquals(listOf(basicSimpleLolView), views.second)
            assertEquals(gigaSimpleGameViews.size, views.first.totalCount)
        }
    }

    @Test
    fun `i can get views returns empty since the page and limit goes beyond actual rows in repository`() {
        runBlocking {
            val page = 2
            val limit = 5
            val repositoryWithState = repository.withState(ViewsState(gigaSimpleGameViews, listOf()))
            assertEquals(
                gigaSimpleGameViews.takeLast(4),
                repositoryWithState.getViews(null, false, page, limit).second
            )
        }
    }


    @Test
    fun `i can get views returns all featured views`() {
        runBlocking {
            val featuredLolView = basicSimpleLolView.copy(id = "4", featured = true)
            val featuredWowView = basicSimpleWowView.copy(id = "3", featured = true)

            val repositoryWithState =
                repository.withState(ViewsState(basicSimpleGameViews.plus(featuredLolView), listOf()))
            assertEquals(
                listOf(featuredWowView, featuredLolView),
                repositoryWithState.getViews(null, true, null, null).second
            )
        }
    }

    @Test
    fun `given a repository with views i can retrieve them`() {
        runBlocking {
            val repositoryWithState = repository.withState(ViewsState(listOf(basicSimpleWowView), listOf()))
            assertEquals(listOf(basicSimpleWowView), repositoryWithState.getOwnViews(owner))
        }
    }

    @Test
    fun `given a repository with views i can retrieve a certain view`() {
        runBlocking {
            val repositoryWithState = repository.withState(ViewsState(listOf(basicSimpleWowView), listOf()))
            assertEquals(basicSimpleWowView, repositoryWithState.get(id))
        }
    }

    @Test
    fun `given an empty repository, trying to retrieve a certain view returns null`() {
        runBlocking {
            assertEquals(null, repository.get(id))
        }
    }

    @Test
    fun `given an empty repository i can insert views`() {
        runBlocking {
            val id = UUID.randomUUID().toString()
            val res = repository.create(id, name, owner, listOf(), Game.WOW, false)
            assertEquals(owner, res.owner)
            assertEquals(name, res.name)
            assertEquals(listOf(), res.entitiesIds)
            assertEquals(id, res.id)
            assertEquals(Game.WOW, res.game)
            assert(repository.state().views.size == 1)
        }
    }

    @Test
    fun `given a repository with a view i can edit it`() {
        runBlocking {
            repository.withState(ViewsState(listOf(basicSimpleWowView), listOf()))
            entitiesRepository.withState(
                EntitiesState(
                    wowEntities = listOf(basicWowEntity),
                    wowHardcoreEntities = listOf(),
                    lolEntities = listOf()
                )
            )
            val res = repository.edit(id, "name2", published, listOf(1L).map { it to "alias" }, featured)
            val finalState = repository.state()
            assertEquals(ViewModified(id, "name2", published, listOf(1), featured), res)
            val viewWithEntities = basicSimpleWowView.copy(name = "name2", entitiesIds = listOf(1))
            assertEquals(
                finalState,
                ViewsState(
                    listOf(viewWithEntities),
                    viewWithEntities.entitiesIds.map { ViewEntity(it, viewWithEntities.id, "alias") })
            )
        }
    }

    @Test
    fun `given a repository with a view i can edit more than one character`() {
        runBlocking {
            repository.withState(
                ViewsState(
                    listOf(basicSimpleWowView),
                    basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") })
            )
            entitiesRepository.withState(
                EntitiesState(
                    wowEntities = (1..4).map {
                        WowEntity(
                            it.toLong(),
                            it.toString(),
                            it.toString(),
                            it.toString(),
                            null
                        )
                    },
                    wowHardcoreEntities = listOf(),
                    lolEntities = listOf()
                )
            )
            val edit = repository.edit(id, "name", published, listOf(1L, 2L, 3L, 4L).map { it to "alias" }, featured)
            val finalState = repository.state()
            assertEquals(ViewModified(id, "name", published, listOf(1, 2, 3, 4), featured), edit)
            val viewWithEntities = basicSimpleWowView.copy(entitiesIds = listOf(1, 2, 3, 4))
            assertEquals(
                finalState,
                ViewsState(
                    listOf(viewWithEntities),
                    viewWithEntities.entitiesIds.map { ViewEntity(it, viewWithEntities.id, "alias") })
            )
        }
    }

    @Test
    fun `given a repository with a view i can delete it`() {
        runBlocking {
            repository.withState(ViewsState(listOf(basicSimpleWowView), listOf()))
            repository.delete(id)
            val finalState = repository.state().views
            assertEquals(finalState, listOf())
        }
    }

    @Test
    fun `given a repository with a view i can patch it`() {
        runBlocking {
            val repo = repository.withState(
                ViewsState(
                    listOf(basicSimpleWowView),
                    basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") })
            )
            val patchedName = "new-name"
            val expectedPatchedView = ViewPatched(basicSimpleWowView.id, patchedName, null, null, true)
            val patch = repo.patch(basicSimpleWowView.id, patchedName, null, null, true)
            val patchedView = repo.state().views.first()
            assertEquals(expectedPatchedView, patch)
            assertEquals(basicSimpleWowView.id, patchedView.id)
            assertEquals(basicSimpleWowView.published, patchedView.published)
            assertEquals(basicSimpleWowView.entitiesIds, patchedView.entitiesIds)
            assertEquals(true, patchedView.featured)
            assertEquals(patchedName, patchedView.name)
        }
    }

    @Test
    fun `given a repository with a view i can patch more than one field`() {
        runBlocking {
            repository.withState(
                ViewsState(
                    listOf(basicSimpleWowView),
                    basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") })
            )
            entitiesRepository.withState(
                EntitiesState(
                    wowEntities = (1..3).map {
                        WowEntity(
                            it.toLong(),
                            it.toString(),
                            it.toString(),
                            it.toString(),
                            null
                        )
                    },
                    wowHardcoreEntities = listOf(),
                    lolEntities = listOf()
                )
            )
            val characters: List<Long> = listOf(1, 2, 3)
            val patchedName = "new-name"
            val patchedPublish = false
            val patch = repository.patch(
                basicSimpleWowView.id,
                patchedName,
                patchedPublish,
                characters.map { it to "alias" },
                featured
            )
            val patchedView = repository.state().views.first()
            assertEquals(ViewPatched(basicSimpleWowView.id, patchedName, patchedPublish, characters, featured), patch)
            assertEquals(basicSimpleWowView.id, patchedView.id)
            assertEquals(patchedPublish, patchedView.published)
            assertEquals(characters, patchedView.entitiesIds)
            assertEquals(patchedName, patchedView.name)
        }
    }

    @Test
    fun `given a repository with view entities i can retrieve then `() {
        runBlocking {
            val lolEntities = listOf(basicLolEntity, basicLolEntity2)
            val alias = "kako"
            val viewEntityOne = ViewEntity(basicLolEntity.id, basicSimpleLolView.id, alias)
            val viewEntityTwo = ViewEntity(basicLolEntity2.id, basicSimpleLolView.id, null)
            entitiesRepository.withState(
                EntitiesState(
                    wowEntities = listOf(),
                    wowHardcoreEntities = listOf(),
                    lolEntities = listOf(basicLolEntity, basicLolEntity2)
                )
            )
            repository.withState(
                ViewsState(
                    listOf(basicSimpleLolView.copy(entitiesIds = lolEntities.map { it.id })),
                    listOf(viewEntityOne, viewEntityTwo)
                )
            )
            assertEquals(
                ViewEntity(viewEntityOne.entityId, basicSimpleLolView.id, alias),
                repository.getViewEntity(basicSimpleLolView.id, viewEntityOne.entityId)
            )
        }
    }
}

class ViewsInMemoryRepositoryTest : ViewsRepositoryTest() {
    override val repository = ViewsInMemoryRepository()
    override val entitiesRepository: EntitiesRepository = EntitiesInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ViewsDatabaseRepositoryTest : ViewsRepositoryTest() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = ViewsDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
    override val entitiesRepository: EntitiesRepository =
        EntitiesDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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