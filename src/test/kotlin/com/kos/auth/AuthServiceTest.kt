package com.kos.auth

import com.auth0.jwt.JWT
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.AuthTestHelper.basicRefreshAuthorization
import com.kos.auth.AuthTestHelper.user
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.common.JWTConfig
import com.kos.common.isDefined
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper.basicCredentials
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.roles.Role
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AuthServiceTest {

    private fun validateToken(
        token: String,
        expectedMode: TokenMode,
        expectedActivities: Set<Activity>,
        userRole: Role
    ) {
        val decodedToken = JWT.decode(token)

        assertEquals(expectedMode, TokenMode.fromString(decodedToken.getClaim("mode").asString()))
        assertEquals(user, decodedToken.getClaim("username").asString())
        assertTrue(decodedToken.issuer.isDefined())

        if (expectedMode == TokenMode.ACCESS) assertEquals(
            expectedActivities,
            decodedToken.getClaim("activities").asList(String::class.java).toSet()
        )

        when (userRole) {
            Role.SERVICE -> assertFalse(decodedToken.expiresAtAsInstant.isDefined())
            else -> assertTrue(decodedToken.expiresAtAsInstant.isDefined())
        }
    }

    @Nested
    inner class BehaviorOfLogin {
        @Test
        fun `i can generate an access and refresh token on login`() {
            runBlocking {
                val credentialsRepositoryState =
                    CredentialsRepositoryState(listOf(basicCredentials), mapOf(user to listOf(Role.USER)))
                val credentialsRepository = CredentialsInMemoryRepository().withState(credentialsRepositoryState)
                val expectedActivities = setOf(Activities.login, Activities.getOwnView)
                val rolesActivitiesRepository = RolesActivitiesInMemoryRepository().withState(
                    mapOf(
                        Role.USER to expectedActivities,
                        Role.ADMIN to setOf(Activities.createViews)
                    )
                )
                val rolesRepository = RolesInMemoryRepository()
                val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
                val credentialsService = CredentialsService(credentialsRepository)
                val authInMemoryRepository = AuthInMemoryRepository()
                val authService =
                    AuthService(authInMemoryRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))
                val loginResponse = authService.login(user)

                loginResponse.onRight {
                    val accessToken = it.accessToken
                    val refreshToken = it.refreshToken

                    assertTrue(accessToken != null)
                    assertTrue(refreshToken != null)
                    validateToken(accessToken, TokenMode.ACCESS, expectedActivities, Role.USER)
                    validateToken(refreshToken, TokenMode.REFRESH, setOf(), Role.USER)
                }.onLeft {
                    fail(it.toString())
                }
            }
        }

        @Test
        fun `i can generate an access token on login with a service account`() {
            runBlocking {
                val credentialsRepositoryState =
                    CredentialsRepositoryState(listOf(basicCredentials), mapOf(user to listOf(Role.SERVICE)))
                val credentialsRepository = CredentialsInMemoryRepository().withState(credentialsRepositoryState)
                val expectedActivities = setOf(Activities.login, Activities.getOwnView)
                val rolesActivitiesRepository = RolesActivitiesInMemoryRepository().withState(
                    mapOf(
                        Role.SERVICE to expectedActivities,
                        Role.ADMIN to setOf(Activities.createViews)
                    )
                )
                val rolesRepository = RolesInMemoryRepository()
                val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
                val credentialsService = CredentialsService(credentialsRepository)
                val authInMemoryRepository = AuthInMemoryRepository()
                val authService =
                    AuthService(authInMemoryRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))
                val loginResponse = authService.login(user)

                loginResponse.onRight {
                    val accessToken = it.accessToken
                    assertTrue(accessToken != null)
                    assertFalse(it.refreshToken.isDefined())
                    validateToken(accessToken, TokenMode.ACCESS, expectedActivities, Role.SERVICE)
                }.onLeft {
                    fail(it.toString())
                }
            }
        }
    }

    @Test
    fun `i can get an access token with a refresh token without needing to login`() {
        runBlocking {
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authInMemoryRepository = AuthInMemoryRepository().withState(
                listOf(basicAuthorization, basicRefreshAuthorization)
            )
            val authService =
                AuthService(authInMemoryRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))
            val result = authService.refresh(user)

            result.onRight { response ->
                val accessToken = response?.accessToken
                assertTrue(accessToken != null)
                validateToken(accessToken, TokenMode.ACCESS, emptySet(), Role.USER)
                assertTrue(response.refreshToken == null)
            }.onLeft { fail(it.toString()) }
        }
    }

    @Test
    fun `i can logout`() {
        runBlocking {
            val credentialsRepository = CredentialsInMemoryRepository()
            val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
            val credentialsService = CredentialsService(credentialsRepository)
            val rolesRepository = RolesInMemoryRepository()
            val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
            val authInMemoryRepository = AuthInMemoryRepository().withState(
                listOf(basicAuthorization, basicRefreshAuthorization)
            )
            val authService =
                AuthService(authInMemoryRepository, credentialsService, rolesService, JWTConfig("issuer", "secret"))

            authService.logout(basicAuthorization.userName)
            assertEquals(0, authInMemoryRepository.state().size)
        }
    }
}