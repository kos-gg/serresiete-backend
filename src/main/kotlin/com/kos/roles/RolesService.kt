package com.kos.roles

import com.kos.activities.Activity
import com.kos.roles.repository.RolesActivitiesRepository
import com.kos.roles.repository.RolesRepository

class RolesService(
    private val rolesRepository: RolesRepository,
    private val rolesActivitiesRepository: RolesActivitiesRepository
) {
    suspend fun getRoles(): List<Role> = rolesRepository.getRoles()
    suspend fun getRole(role: Role): Pair<Role, Set<Activity>> =
        Pair(role, rolesActivitiesRepository.getActivitiesFromRole(role))
    suspend fun setActivitiesToRole(role: Role, activities: Set<Activity>) {
        rolesActivitiesRepository.updateActivitiesFromRole(role, activities)
    }

}