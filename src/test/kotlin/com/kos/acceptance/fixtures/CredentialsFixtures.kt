package com.kos.acceptance.fixtures

import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.roles.Role
import org.jetbrains.exposed.sql.Database

suspend fun givenUser(
    db: Database,
    username: String = "sanxei",
    role: Role = Role.ADMIN
) {
    val repo = CredentialsDatabaseRepository(db)
    repo.insertCredentials(username, "test-password")
    repo.insertRoles(username, setOf(role))
}
