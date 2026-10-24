package com.kos.credentials

import arrow.core.Either
import com.kos.common.error.UserAlreadyExists
import com.kos.common.isDefined
import com.kos.credentials.repository.CredentialsRepository
import com.kos.roles.Role
import org.mindrot.jbcrypt.BCrypt

class CredentialsService(
    private val credentialsRepository: CredentialsRepository,
) {
    suspend fun createCredentials(createCredentialRequest: CreateCredentialRequest): Either<UserAlreadyExists, Unit> =
        credentialsRepository.insertCredentials(
            createCredentialRequest.userName,
            password = BCrypt.hashpw(
                createCredentialRequest.password,
                BCrypt.gensalt(12)
            )
        ).map { credentialsRepository.insertRoles(createCredentialRequest.userName, createCredentialRequest.roles) }

    suspend fun validateCredentials(credentials: Credentials): Boolean =
        credentialsRepository.getCredentials(credentials.userName)
            ?.takeIf { BCrypt.checkpw(credentials.password, it.password) }.isDefined()


    suspend fun editCredential(userName: String, request: EditCredentialRequest) {
        credentialsRepository.editCredentials(
            userName,
            BCrypt.hashpw(request.password, BCrypt.gensalt(12))
        )
        credentialsRepository.updateRoles(userName, request.roles)
    }

    suspend fun getUserRoles(userName: String): List<Role> = credentialsRepository.getUserRoles(userName)

    suspend fun getCredentials(): List<CredentialsWithRoles> =
        credentialsRepository.getCredentials().map {
            CredentialsWithRoles(it.userName, getUserRoles(it.userName))
        }

    suspend fun deleteCredentials(user: String) = credentialsRepository.deleteCredentials(user)
    suspend fun getCredential(user: String): CredentialsWithRoles? =
        credentialsRepository.getCredentials(user)?.let {
            CredentialsWithRoles(it.userName, getUserRoles(it.userName))
        }

    suspend fun patchCredential(userName: String, request: PatchCredentialRequest) {
        credentialsRepository.patch(
            userName,
            request.copy(password = request.password?.let { BCrypt.hashpw(it, BCrypt.gensalt(12)) })
        )
    }
}