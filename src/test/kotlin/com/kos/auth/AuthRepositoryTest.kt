package com.kos.auth

import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.AuthTestHelper.token
import com.kos.auth.AuthTestHelper.user
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.auth.repository.AuthRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

abstract class AuthRepositoryTest {

    abstract val repository: AuthRepository

    @Test
    fun `given a repository with one authorization i can retrieve it`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(basicAuthorization))
            assertEquals(repositoryWithState.getAuthorization(token), basicAuthorization)
        }
    }

    @Test
    fun `given an empty repository i can insert an authorization`() {
        runBlocking {
            val token =
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6Im9zY2FyIiwiYWN0aXZpdGllcyI6WyJhZGQgYWN0aXZpdHkgdG8gcm9sZSIsImFkZCByb2xlIHRvIHVzZXIiLCJjcmVhdGUgYSB2aWV3IiwiY3JlYXRlIGFjdGl2aXRpZXMiLCJjcmVhdGUgY3JlZGVudGlhbHMiLCJjcmVhdGUgcm9sZXMiLCJkZWxldGUgYWN0aXZpdGllcyIsImRlbGV0ZSBhY3Rpdml0eSBmcm9tIHJvbGUiLCJkZWxldGUgYW55IHZpZXciLCJkZWxldGUgY3JlZGVudGlhbHMiLCJkZWxldGUgb3duIHZpZXciLCJkZWxldGUgcm9sZSBmcm9tIHVzZXIiLCJkZWxldGUgcm9sZXMiLCJlZGl0IGFueSB2aWV3IiwiZWRpdCBjcmVkZW50aWFscyIsImVkaXQgb3duIHZpZXciLCJnZXQgYW55IGFjdGl2aXRpZXMiLCJnZXQgYW55IGNyZWRlbnRpYWxzIiwiZ2V0IGFueSBjcmVkZW50aWFscyByb2xlcyIsImdldCBhbnkgcm9sZXMiLCJnZXQgYW55IHZpZXciLCJnZXQgYW55IHZpZXdzIiwiZ2V0IG93biBjcmVkZW50aWFscyByb2xlcyIsImdldCBvd24gdmlldyIsImdldCBvd24gdmlld3MiLCJnZXQgdGFzayIsImdldCB0YXNrcyIsImdldCB2aWV3IGNhY2hlZCBkYXRhIiwiZ2V0IHZpZXcgZGF0YSIsImxvZ2luIiwibG9nb3V0IiwicnVuIHRhc2siXX0.7FhxhHM0VtRTmWsu4Oy7A_dLroiO0jMFIDq_8ZnDrOQ"
            val userName = repository.insertToken(user, token, isAccess = true)?.userName
            assertEquals(user, userName)
            val finalStateOfAuthorizations = repository.state()
            assertContains(finalStateOfAuthorizations.map { it.token }, token)
            assertContains(finalStateOfAuthorizations.map { it.userName }, user)
        }
    }

    @Test
    fun `given a repository with one authorization i can delete it`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(basicAuthorization))
            assertTrue(repositoryWithState.deleteTokensFromUser(basicAuthorization.userName))
            assertTrue(repositoryWithState.state().isEmpty())
        }
    }

    @Test
    fun `given a repository with one authorization i cannot insert the same token`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(basicAuthorization))
            assertThrows<Exception> { repositoryWithState.insertToken("differentUser", token, isAccess = false) }
            assertTrue(repositoryWithState.state().size == 1)
        }
    }
}

class AuthInMemoryRepositoryTest : AuthRepositoryTest() {
    override val repository = AuthInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        repository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthDatabaseRepositoryTest : AuthRepositoryTest() {

    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository: AuthRepository =
        AuthDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

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
