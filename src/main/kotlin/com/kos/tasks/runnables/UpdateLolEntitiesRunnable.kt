package com.kos.tasks.runnables

import com.kos.common.WithLogger
import com.kos.tasks.TasksService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*

data class UpdateLolEntitiesRunnable(
    val tasksService: TasksService,
    val coroutineScope: CoroutineScope,
) : Runnable, WithLogger("updateLolEntities") {

    override fun run() {
        coroutineScope.launch {
            logger.info("Running update lol entities task")
            tasksService.updateLolEntities(UUID.randomUUID().toString())
            logger.info("Finished running lol entities task")
        }
    }
}