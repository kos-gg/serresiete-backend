package com.kos.activities

import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialsDomainTest {

    @Test
    fun `assert activity values`() {
        assertEquals("delete activity from role", Activities.deleteActivityFromRole)
        assertEquals("add activity to role", Activities.addActivityToRole)
        assertEquals("delete credentials", Activities.deleteCredentials)
        assertEquals("get any credentials", Activities.getAnyCredentials)
        assertEquals("get any view", Activities.getAnyView)
        assertEquals("create a view", Activities.createViews)
        assertEquals("get any views", Activities.getAnyViews)
        assertEquals("get own views", Activities.getOwnViews)
        assertEquals("get own view", Activities.getOwnView)
        assertEquals("get view data", Activities.getViewData)
        assertEquals("get view cached data", Activities.getViewCachedData)
        assertEquals("edit own view", Activities.editOwnView)
        assertEquals("edit any view", Activities.editAnyView)
        assertEquals("delete own view", Activities.deleteOwnView)
        assertEquals("delete any view", Activities.deleteAnyView)
        assertEquals("create credentials", Activities.createCredentials)
        assertEquals("edit credentials", Activities.editCredentials)
        assertEquals("get any credentials roles", Activities.getAnyCredentialsRoles)
        assertEquals("get own credentials roles", Activities.getOwnCredentialsRoles)
        assertEquals("add role to user", Activities.addRoleToUser)
        assertEquals("get any activities", Activities.getAnyActivities)
        assertEquals("get any roles", Activities.getAnyRoles)
        assertEquals("create activities", Activities.createActivities)
        assertEquals("create roles", Activities.createRoles)
        assertEquals("delete activities", Activities.deleteActivities)
        assertEquals("delete roles", Activities.deleteRoles)
        assertEquals("login", Activities.login)
        assertEquals("logout", Activities.logout)
        assertEquals("delete role from user", Activities.deleteRoleFromUser)
        assertEquals("run task", Activities.runTask)
        assertEquals("get task", Activities.getTask)
        assertEquals("get tasks", Activities.getTasks)
        assertEquals("get task types", Activities.getTaskTypes)
    }
}