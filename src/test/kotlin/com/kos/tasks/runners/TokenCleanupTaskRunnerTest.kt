package com.kos.tasks.runners

import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.auth.AuthService
import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.common.JWTConfig
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenCleanupTaskRunnerTest {

    private val authRepo = AuthInMemoryRepository()
    private val authService = AuthService(
        authRepo,
        CredentialsService(CredentialsInMemoryRepository()),
        RolesService(RolesInMemoryRepository(), RolesActivitiesInMemoryRepository()),
        JWTConfig("issuer", "secret")
    )
    private val runner = TokenCleanupTaskRunner(authService)

    @Test
    fun `expired tokens are deleted and task is recorded as successful`() = runBlocking {
        authRepo.withState(
            listOf(
                basicAuthorization,
                basicAuthorization.copy(validUntil = OffsetDateTime.now().minusHours(1))
            )
        )

        val result = runner.run(null)

        assertEquals(listOf(basicAuthorization), authRepo.state())
        assertEquals(Either.Right(TaskCompleted()), result)
    }
}
