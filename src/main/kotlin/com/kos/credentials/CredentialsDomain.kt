package com.kos.credentials

import com.kos.roles.Role
import kotlinx.serialization.Serializable

@Serializable
data class Credentials(val userName: String, val password: String)

@Serializable
data class CredentialsWithRoles(val userName: String, val roles: List<Role>)

@Serializable
data class CreateCredentialRequest(val userName: String, val password: String, val roles: Set<Role>)

@Serializable
data class EditCredentialRequest(val password: String, val roles: Set<Role>)

@Serializable
data class PatchCredentialRequest(val password: String? = null, val roles: Set<Role>? = null)

data class CredentialsRole(val userName: String, val role: Role)

