package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.domain.raiderio.ExpansionSeasons
import com.kos.clients.domain.raiderio.Season
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionInMemoryRepository
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionState
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateMythicPlusSeasonTaskRunnerTest {

    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val wowExpansionRepository = WowExpansionInMemoryRepository()
    private val wowSeasonsRepository = WowSeasonInMemoryRepository()
    private val wowSeasonsService = WowSeasonService(wowExpansionRepository, wowSeasonsRepository, raiderIoClient)
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = UpdateMythicPlusSeasonTaskRunner(tasksRepo, wowSeasonsService)

    @Test
    fun `new mythic plus season is added and task is recorded as successful`() = runBlocking {
        wowExpansionRepository.withState(WowExpansionState(listOf(WowExpansion(10, "TWW", true))))
        `when`(raiderIoClient.getExpansionSeasons(10))
            .thenReturn(Either.Right(ExpansionSeasons(listOf(Season(true, "TWW3", "tww-3", 15, listOf())))))

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(15, wowSeasonsRepository.state().wowSeasons[0].id)
        assertEquals(id, task.id)
        assertEquals(TaskType.UPDATE_MYTHIC_PLUS_SEASON, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }
}
