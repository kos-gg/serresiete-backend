package com.kos.credentials.repository

import arrow.core.Either
import com.kos.common.WithState
import com.kos.common.error.InsertError
import com.kos.credentials.Credentials
import com.kos.credentials.PatchCredentialRequest
import com.kos.roles.Role

interface CredentialsRepository : WithState<CredentialsRepositoryState, CredentialsRepository> {
    suspend fun getCredentials(): List<Credentials>
    suspend fun getCredentials(userName: String): Credentials?
    suspend fun insertCredentials(userName: String, password: String): Either<InsertError, Unit>
    suspend fun editCredentials(userName: String, newPassword: String)
    suspend fun getUserRoles(userName: String): List<Role>
    suspend fun insertRoles(userName: String, roles: Set<Role>)
    suspend fun updateRoles(userName: String, roles: Set<Role>)
    suspend fun deleteCredentials(user: String)
    suspend fun patch(userName: String, request: PatchCredentialRequest)
}

data class CredentialsRepositoryState(
    val users: List<Credentials>,
    val credentialsRoles: Map<String, List<Role>>
)