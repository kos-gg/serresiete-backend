package com.kos.credentials.repository

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.error.InsertError
import com.kos.credentials.Credentials
import com.kos.credentials.PatchCredentialRequest
import com.kos.roles.Role

class CredentialsInMemoryRepository : CredentialsRepository, InMemoryRepository {
    private val users = mutableListOf<Credentials>()
    private val userRoles = mutableMapOf<String, List<Role>>()
    override suspend fun getCredentials(): List<Credentials> {
        return users
    }

    override suspend fun getCredentials(userName: String): Credentials? {
        return users.find { it.userName == userName }
    }

    override suspend fun insertCredentials(userName: String, password: String): Either<InsertError, Unit> {
        return if (users.map { it.userName }.contains(userName)) Either.Left(InsertError("Duplicated user $userName"))
        else {
            users.add(Credentials(userName, password))
            Either.Right(Unit)
        }
    }

    override suspend fun editCredentials(userName: String, newPassword: String) {
        val index = users.map { it.userName }.indexOf(userName)
        users[index] = Credentials(userName, newPassword)
    }

    override suspend fun getUserRoles(userName: String): List<Role> =
        userRoles[userName].orEmpty()

    override suspend fun insertRoles(userName: String, roles: Set<Role>) {
        userRoles.compute(userName) { _, currentRoles -> (currentRoles ?: mutableListOf()) + roles }
    }

    override suspend fun updateRoles(userName: String, roles: Set<Role>) {
        userRoles[userName] = roles.toList()
    }

    override suspend fun deleteCredentials(user: String) {
        val index = users.map { it.userName }.indexOf(user)
        users.removeAt(index)
    }

    override suspend fun patch(userName: String, request: PatchCredentialRequest) {
        request.password?.let { password ->
            val userIndex = users.indexOfFirst { it.userName == userName }
            users.removeAt(userIndex)
            val newCredential = Credentials(userName, password)
            users.add(userIndex, newCredential)
        }

        request.roles?.let { roles ->
            if (roles.isEmpty()) userRoles.remove(userName)
            else userRoles[userName] = roles.toList()
        }
    }

    override suspend fun state(): CredentialsRepositoryState {
        return CredentialsRepositoryState(users, userRoles)
    }

    override suspend fun withState(initialState: CredentialsRepositoryState): CredentialsInMemoryRepository {
        users.addAll(initialState.users)
        userRoles.putAll(initialState.credentialsRoles)
        return this
    }

    override fun clear() {
        users.clear()
        userRoles.clear()
    }
}