package com.kos.roles

import com.kos.activities.Activity
import kotlinx.serialization.Serializable

enum class Role {
    ADMIN {
        override val maxNumberOfViews: Int = Int.MAX_VALUE
        override val maxNumberOfEntities: Int = Int.MAX_VALUE
        override fun toString(): String = "admin"
    },
    USER {
        override val maxNumberOfViews: Int = 2
        override val maxNumberOfEntities: Int = 10
        override fun toString(): String = "user"
    },
    SERVICE {
        override val maxNumberOfViews: Int = 0
        override val maxNumberOfEntities: Int = 0
        override fun toString(): String = "service"
    };

    abstract val maxNumberOfViews: Int
    abstract val maxNumberOfEntities: Int

    companion object {
        fun fromString(value: String): Role = when (value) {
            "admin" -> ADMIN
            "user" -> USER
            "service" -> SERVICE
            else -> throw IllegalArgumentException("Unknown role: $value")
        }
    }
}

@Serializable
data class RoleRequest(val role: Role)

@Serializable
data class ActivitiesRequest(val activities: Set<Activity>)