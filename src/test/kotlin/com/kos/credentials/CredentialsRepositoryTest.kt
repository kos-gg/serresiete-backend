package com.kos.credentials

import com.kos.common.isDefined
import com.kos.credentials.CredentialsTestHelper.basicCredentialsInitialState
import com.kos.credentials.CredentialsTestHelper.basicCredentialsWithRolesInitialState
import com.kos.credentials.CredentialsTestHelper.encryptedCredentials
import com.kos.credentials.CredentialsTestHelper.user
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepository
import com.kos.roles.Role
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class CredentialsRepositoryTest {

    abstract val repository: CredentialsRepository

    @Test
    open fun `given a repository with credentials i can retrieve all of them`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicCredentialsInitialState)
            assertEquals(repositoryWithState.getCredentials(), listOf(encryptedCredentials))
        }
    }

    @Test
    open fun `given a repository with credentials i can retrieve them`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicCredentialsInitialState)
            assertEquals(repositoryWithState.getCredentials(user), encryptedCredentials)
        }
    }

    @Test
    open fun `given an empty repository i can insert credentials`() {
        runBlocking {
            assertTrue(repository.state().users.isEmpty())
            repository.insertCredentials(encryptedCredentials.userName, encryptedCredentials.password)
            assertTrue(repository.state().users.size == 1)
            assertTrue(repository.state().users.all { it.userName == user && it.password == encryptedCredentials.password })
        }
    }

    @Test
    open fun `given an empty repository i can batch insert roles to a credential`() {
        runBlocking {
            assertTrue(repository.state().users.isEmpty())
            repository.insertRoles(user, setOf(Role.USER, Role.ADMIN, Role.SERVICE))
            assertEquals(3, repository.state().credentialsRoles[user]?.size)
            assertEquals(listOf(Role.USER, Role.ADMIN, Role.SERVICE), repository.state().credentialsRoles[user])
        }
    }

    @Test
    open fun `given a repository with credentials i can edit them`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicCredentialsInitialState)
            repositoryWithState.editCredentials(user, "newPassword")
            assertTrue(repositoryWithState.state().users.contains(Credentials(user, "newPassword")))
        }
    }

    @Test
    open fun `given a repository and a user with roles i can retrieve it's roles`() {
        runBlocking {
            val repositoryWithState = repository.withState(
                basicCredentialsInitialState.copy(
                    credentialsRoles = mapOf(user to listOf(Role.USER, Role.ADMIN))
                )
            )

            val roles = repositoryWithState.getUserRoles(user)
            assertEquals(listOf(Role.USER, Role.ADMIN), roles)
        }
    }

    @Test
    open fun `given a repository with credentials i can delete them`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicCredentialsInitialState)
            repositoryWithState.deleteCredentials(user)

            assertEquals(listOf(), repositoryWithState.state().users)
        }
    }

    @Test
    open fun `given a repository with credentials and roles i can patch them`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicCredentialsWithRolesInitialState)
            repositoryWithState.patch(user, PatchCredentialRequest("newPassword", setOf()))
            assertTrue(repositoryWithState.state().users.contains(Credentials(user, "newPassword")))
            assertFalse(repositoryWithState.state().credentialsRoles[user].isDefined())
        }
    }

    @Test
    open fun `given a repository with credentials and roles i can patch them updating roles`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicCredentialsWithRolesInitialState)
            val expected = setOf(Role.ADMIN)
            repositoryWithState.patch(user, PatchCredentialRequest(null, expected))
            assertEquals(expected.toList(), repositoryWithState.state().credentialsRoles[user])
        }
    }

    @Test
    open fun `given a repository with credentials and roles i can update roles`() {
        runBlocking {
            val repositoryWithState = repository.withState(basicCredentialsWithRolesInitialState)
            repositoryWithState.updateRoles(user, setOf(Role.SERVICE))
            assertEquals(listOf(Role.SERVICE), repositoryWithState.state().credentialsRoles[user])
        }
    }

    @Test
    open fun `given a repository with a credential i cant insert it again`() {
        runBlocking {
            repository.withState(basicCredentialsInitialState)
            repository.insertCredentials(CredentialsTestHelper.user, CredentialsTestHelper.password)
                .onRight { fail() }
                .onLeft { assertEquals(it.message, "Duplicated user $user") }
        }
    }

}

class CredentialsInMemoryRepositoryTest : CredentialsRepositoryTest() {
    override val repository = CredentialsInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CredentialsDatabaseRepositoryTest : CredentialsRepositoryTest() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository: CredentialsRepository =
        CredentialsDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
