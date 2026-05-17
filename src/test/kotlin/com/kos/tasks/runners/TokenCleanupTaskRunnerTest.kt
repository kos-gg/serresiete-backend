package com.kos.tasks.runners

import com.kos.auth.AuthService
import com.kos.auth.AuthTestHelper.basicAuthorization
import com.kos.auth.repository.AuthInMemoryRepository
import com.kos.common.JWTConfig
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.roles.repository.RolesInMemoryRepository
import com.kos.tasks.Status
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenCleanupTaskRunnerTest {

    private val tasksRepo = TasksInMemoryRepository()
    private val authRepo = AuthInMemoryRepository()
    private val authService = AuthService(
        authRepo,
        CredentialsService(CredentialsInMemoryRepository()),
        RolesService(RolesInMemoryRepository(), RolesActivitiesInMemoryRepository()),
        JWTConfig("issuer", "secret")
    )
    private val runner = TokenCleanupTaskRunner(tasksRepo, authService)

    @Test
    fun `expired tokens are deleted and task is recorded as successful`() = runBlocking {
        authRepo.withState(
            listOf(
                basicAuthorization,
                basicAuthorization.copy(validUntil = OffsetDateTime.now().minusHours(1))
            )
        )
        val id = UUID.randomUUID().toString()

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(listOf(basicAuthorization), authRepo.state())
        assertEquals(id, task.id)
        assertEquals(TaskType.TOKEN_CLEANUP_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }
}
