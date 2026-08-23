package com.kos.credentials

import com.kos.credentials.CredentialsTestHelper.basicCredentials
import com.kos.credentials.CredentialsTestHelper.basicCredentialsInitialState
import com.kos.credentials.CredentialsTestHelper.basicCredentialsWithRoles
import com.kos.credentials.CredentialsTestHelper.basicCredentialsWithRolesInitialState
import com.kos.credentials.CredentialsTestHelper.encryptedCredentials
import com.kos.credentials.CredentialsTestHelper.password
import com.kos.credentials.CredentialsTestHelper.user
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.roles.Role
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CredentialsServiceTest {
    @Test
    fun `i can validate credentials`() {
        runBlocking {
            val credentialsInMemoryRepository = CredentialsInMemoryRepository().withState(basicCredentialsInitialState)
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)
            assertTrue(credentialsService.validateCredentials(basicCredentials))
        }
    }

    @Test
    fun `i can create credentials`() {
        runBlocking {
            val credentialsInMemoryRepository = CredentialsInMemoryRepository()
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)

            credentialsService.createCredentials(CreateCredentialRequest(user, password, setOf()))
            assertEquals(credentialsInMemoryRepository.state().users.map { it.userName }, listOf(user))
        }
    }

    @Test
    fun `i can get a credential`() {
        runBlocking {
            val credentialsInMemoryRepository = CredentialsInMemoryRepository().withState(basicCredentialsInitialState)
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)

            assertEquals(CredentialsWithRoles(user, listOf()), credentialsService.getCredential(user))
        }
    }

    @Test
    fun `i can edit credentials`() {
        runBlocking {
            val credentialsInMemoryRepository =
                CredentialsInMemoryRepository().withState(basicCredentialsInitialState)
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)

            assertEquals(credentialsInMemoryRepository.getCredentials(user), encryptedCredentials)
            credentialsService.editCredential(user, EditCredentialRequest("newPassword", setOf()))
            assertNotEquals(credentialsInMemoryRepository.getCredentials(user)?.password, encryptedCredentials.password)
        }
    }

    @Test
    fun `i can patch credentials`() {
        runBlocking {
            val credentialsInMemoryRepository =
                CredentialsInMemoryRepository().withState(basicCredentialsInitialState)
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)

            assertEquals(credentialsInMemoryRepository.getCredentials(user), encryptedCredentials)
            credentialsService.patchCredential(user, PatchCredentialRequest("newPassword", null))
            assertNotEquals(credentialsInMemoryRepository.getCredentials(user)?.password, encryptedCredentials.password)
        }
    }

    @Test
    fun `i can get user roles`() {
        runBlocking {
            val credentialsInMemoryRepository =
                CredentialsInMemoryRepository().withState(basicCredentialsWithRolesInitialState)
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)

            assertEquals(credentialsService.getUserRoles(user), listOf(Role.USER))
        }
    }

    @Test
    fun `i can get all credentials`() {
        runBlocking {
            val credentialsInMemoryRepository =
                CredentialsInMemoryRepository().withState(basicCredentialsWithRolesInitialState)
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)

            assertEquals(credentialsService.getCredentials(), listOf(basicCredentialsWithRoles))
        }
    }

    @Test
    fun `i can delete credentials`() {
        runBlocking {
            val credentialsInMemoryRepository =
                CredentialsInMemoryRepository().withState(basicCredentialsWithRolesInitialState)
            val credentialsService =
                CredentialsService(credentialsInMemoryRepository)

            credentialsService.deleteCredentials(user)
            assertEquals(credentialsInMemoryRepository.state().users, listOf())
        }
    }
}