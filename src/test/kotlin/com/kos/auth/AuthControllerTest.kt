package com.kos.auth

import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.assertTrue
import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.common.JWTConfig
import com.kos.common.isDefined
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper.basicCredentials
import com.kos.credentials.CredentialsTestHelper.emptyCredentialsState
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.roles.Role
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.views.ViewsTestHelper.owner
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test

//TODO - this tests should be more exhaustive

class AuthControllerTest {
    private val credentialsRepository = CredentialsInMemoryRepository()
    private val rolesRepository = RolesInMemoryRepository()
    private val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
    private val authRepository = AuthInMemoryRepository()

    private suspend fun createController(
        credentialsState: CredentialsRepositoryState,
        rolesActivitiesState: Map<Role, Set<Activity>>,
        rolesState: List<Role>,
        authState: List<Authorization>
    ): AuthController {
        val credentialsRepositoryWithState = credentialsRepository.withState(credentialsState)
        val rolesActivitiesRepositoryWithState = rolesActivitiesRepository.withState(rolesActivitiesState)
        val authRepositoryWithState = authRepository.withState(authState)
        val rolesRepositoryWithState = rolesRepository.withState(rolesState)

        val rolesService = RolesService(rolesRepositoryWithState, rolesActivitiesRepositoryWithState)
        val credentialsService = CredentialsService(credentialsRepositoryWithState)
        val authService =
            AuthService(authRepositoryWithState, credentialsService, rolesService, JWTConfig("issuer", "secret"))
        return AuthController(authService)
    }


    @BeforeTest
    fun beforeEach() {
        credentialsRepository.clear()
        rolesActivitiesRepository.clear()
        authRepository.clear()
    }

    @Test
    fun `i can login`() {
        runBlocking {
            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = "owner")),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                mapOf(Pair(Role.USER, setOf(Activities.login))),
                listOf(),
                listOf()
            )
            val res = controller.login("owner").getOrNull()

            assertTrue(res?.accessToken?.isNotEmpty())
            assertTrue(res?.refreshToken?.isNotEmpty())
        }
    }

    @Test
    fun `i can logout`() {
        runBlocking {
            val controller = createController(
                emptyCredentialsState,
                mapOf(),
                listOf(),
                listOf(basicAuthorization.copy(userName = "owner"))
            )
            assertTrue(controller.logout("owner", setOf(Activities.logout)).getOrNull())
        }
    }

    @Test
    fun `I can refresh tokens`() {
        runBlocking {
            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = "owner")),
                mapOf()
            )

            val controller = createController(
                credentialsState,
                mapOf(),
                listOf(),
                listOf(basicAuthorization.copy(userName = "owner").copy(isAccess = false))
            )

            val res = controller.refresh(owner).getOrNull()
            assertTrue(res.isDefined())
        }
    }

}