package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType

class UpdateMythicPlusSeasonTaskRunner(
    private val wowSeasonsService: WowSeasonService
) : TaskRunner, WithLogger("updateMythicPlusSeasonTaskRunner") {

    override val type = TaskType.UPDATE_MYTHIC_PLUS_SEASON

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Running $type")
        val season = wowSeasonsService.addNewMythicPlusSeason()
        logger.info("Updated Wow Season to season ${season.id} - ${season.name}")
        TaskCompleted()
    }
}
