package com.kos.tasks

import arrow.core.Either
import com.kos.common.InvalidTaskType
import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class Task(
    val id: String,
    val type: TaskType,
    val taskStatus: TaskStatus,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val inserted: OffsetDateTime
)

@Serializable
data class TaskStatus(val status: Status, val message: String?)

@Serializable
enum class Status {
    SUCCESSFUL {
        override fun toString(): String = "successful"
    },
    ERROR {
        override fun toString(): String = "error"
    };

    companion object {
        fun fromString(value: String): Status = when (value) {
            "successful" -> SUCCESSFUL
            "error" -> ERROR
            else -> throw IllegalArgumentException("Unknown status: $value")
        }
    }
}

@Serializable
enum class TaskType {
    CACHE_WOW_DATA_TASK {
        override fun toString(): String = "cacheWowDataTask"
    },
    CACHE_WOW_HC_DATA_TASK {
        override fun toString(): String = "cacheWowHcDataTask"
    },
    CACHE_LOL_DATA_TASK {
        override fun toString(): String = "cacheLolDataTask"
    },
    TOKEN_CLEANUP_TASK {
        override fun toString(): String = "tokenCleanupTask"
    },
    TASK_CLEANUP_TASK {
        override fun toString(): String = "taskCleanupTask"
    },
    UPDATE_LOL_ENTITIES_TASK {
        override fun toString(): String = "updateLolEntitiesTask"
    },
    CACHE_CLEAR_TASK {
        override fun toString(): String = "cacheClearTask"
    },
    TASK_UPDATE_MYTHIC_PLUS_SEASON {
        override fun toString(): String = "updateMythicPlusSeason"
    };

    companion object {
        fun fromString(value: String): Either<InvalidTaskType, TaskType> = when (value) {
            "cacheWowDataTask" -> Either.Right(CACHE_WOW_DATA_TASK)
            "cacheWowHcDataTask" -> Either.Right(CACHE_WOW_HC_DATA_TASK)
            "cacheLolDataTask" -> Either.Right(CACHE_LOL_DATA_TASK)
            "tokenCleanupTask" -> Either.Right(TOKEN_CLEANUP_TASK)
            "taskCleanupTask" -> Either.Right(TASK_CLEANUP_TASK)
            "updateLolEntitiesTask" -> Either.Right(UPDATE_LOL_ENTITIES_TASK)
            "cacheClearTask" -> Either.Right(CACHE_CLEAR_TASK)
            "updateMythicPlusSeason" -> Either.Right(TASK_UPDATE_MYTHIC_PLUS_SEASON)
            else -> Either.Left(InvalidTaskType(value))
        }
    }
}

@Serializable
data class TaskRequest(val type: TaskType, val arguments: Map<String, String>? = null)