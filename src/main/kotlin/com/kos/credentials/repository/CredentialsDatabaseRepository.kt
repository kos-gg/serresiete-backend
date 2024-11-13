package com.kos.credentials.repository

import com.kos.credentials.Credentials
import com.kos.credentials.CredentialsRole
import com.kos.credentials.PatchCredentialRequest
import com.kos.roles.Role
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class CredentialsDatabaseRepository(private val db: Database) : CredentialsRepository {
    override suspend fun withState(initialState: CredentialsRepositoryState): CredentialsDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Users.batchInsert(initialState.users) {
                this[Users.userName] = it.userName
                this[Users.password] = it.password
            }
            CredentialsRoles.batchInsert(initialState.credentialsRoles.flatMap { (userName, roles) ->
                roles.map { Pair(userName, it) }
            }) {
                this[CredentialsRoles.userName] = it.first
                this[CredentialsRoles.role] = it.second.toString()
            }
        }
        return this
    }

    object Users : Table() {
        val userName = varchar("user_name", 48)
        val password = varchar("password", 128)

        override val primaryKey = PrimaryKey(userName)
    }

    private fun resultRowToUser(row: ResultRow) = Credentials(
        row[Users.userName],
        row[Users.password]
    )

    override suspend fun getCredentials(): List<Credentials> {
        return newSuspendedTransaction(Dispatchers.IO, db) { Users.selectAll().map { resultRowToUser(it) } }
    }

    override suspend fun getCredentials(userName: String): Credentials? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Users.select { Users.userName.eq(userName) }.map { resultRowToUser(it) }.singleOrNull()
        }
    }

    override suspend fun insertCredentials(userName: String, password: String) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Users.insert {
                it[Users.userName] = userName
                it[Users.password] = password
            }
        }
    }

    object CredentialsRoles : Table("credentials_roles") {
        val userName = varchar("user_name", 48)
        val role = varchar("role", 48)

        override val primaryKey = PrimaryKey(userName, role)
    }

    private fun resultRowToCredentialsRoles(row: ResultRow) = CredentialsRole(
        row[CredentialsRoles.userName],
        Role.fromString(row[CredentialsRoles.role])
    )

    override suspend fun editCredentials(userName: String, newPassword: String) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Users.update({ Users.userName.eq(userName) }) {
                it[password] = newPassword
            }
        }
    }

    override suspend fun getUserRoles(userName: String): List<Role> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            CredentialsRoles.select { CredentialsRoles.userName.eq(userName) }
                .map { resultRowToCredentialsRoles(it).role }
        }
    }

    override suspend fun insertRoles(userName: String, roles: Set<Role>) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            CredentialsRoles.batchInsert(roles) {
                this[CredentialsRoles.userName] = userName
                this[CredentialsRoles.role] = it.toString()
            }
        }
    }

    override suspend fun updateRoles(userName: String, roles: Set<Role>) {
        //TODO: Rollback when insert goes wrong
        newSuspendedTransaction(Dispatchers.IO, db) {
            Users.deleteWhere { Users.userName.eq(userName) }
            CredentialsRoles.batchInsert(roles) {
                this[CredentialsRoles.userName] = userName
                this[CredentialsRoles.role] = it.toString()
            }
        }
    }

    override suspend fun deleteCredentials(user: String) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Users.deleteWhere { userName.eq(user) }
        }
    }

    override suspend fun patch(userName: String, request: PatchCredentialRequest) {
        newSuspendedTransaction {
            request.password?.let {
                Users.update({ Users.userName.eq(userName) }) {
                    it[password] = request.password
                }
            }
            request.roles?.let {
                CredentialsRoles.deleteWhere { CredentialsRoles.userName.eq(userName) }
                CredentialsRoles.deleteWhere {
                    role.eq(role.toString()) and CredentialsRoles.userName.eq(
                        userName
                    )
                }
            }
        }
    }

    override suspend fun state(): CredentialsRepositoryState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            CredentialsRepositoryState(
                Users.selectAll().map { resultRowToUser(it) },
                CredentialsRoles.selectAll().map { resultRowToCredentialsRoles(it) }.groupBy { it.userName }
                    .mapValues { it.value.map { cr -> cr.role } }
            )
        }
    }
}