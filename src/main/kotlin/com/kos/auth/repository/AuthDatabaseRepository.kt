package com.kos.auth.repository

import com.kos.auth.Authorization
import com.kos.common.DatabaseFactory.dbQuery
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.util.*

class AuthDatabaseRepository(private val db: Database) : AuthRepository {

    private val daysBeforeAccessTokenExpires: Long = 1
    private val daysBeforeRefreshTokenExpires: Long = 30

    override suspend fun withState(initialState: List<Authorization>): AuthDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Authorizations.batchInsert(initialState) {
                this[Authorizations.userName] = it.userName
                this[Authorizations.token] = it.token
                this[Authorizations.lastUsed] = it.lastUsed.toString()
                this[Authorizations.validUntil] = it.validUntil?.toString()
                this[Authorizations.isAccess] = it.isAccess
            }
        }
        return this
    }

    object Authorizations : Table() {
        val userName = varchar("user_name", 48)
        val token = varchar("token", 128)
        val lastUsed = text("last_used")
        val validUntil = text("valid_until").nullable()
        val isAccess = bool("is_access")

        override val primaryKey = PrimaryKey(token)
    }

    private fun resultRowToAuthorization(row: ResultRow) = Authorization(
        row[Authorizations.userName],
        row[Authorizations.token],
        OffsetDateTime.parse(row[Authorizations.lastUsed]),
        row[Authorizations.validUntil]?.let {
            OffsetDateTime.parse(it)
        },
        row[Authorizations.isAccess]
    )

    override suspend fun insertToken(userName: String, isAccess: Boolean): Authorization? =
        newSuspendedTransaction(Dispatchers.IO, db) {
            val insertStatement = Authorizations.insert {
                it[Authorizations.userName] = userName
                it[token] = UUID.randomUUID().toString()
                it[lastUsed] = OffsetDateTime.now().toString()
                it[validUntil] = OffsetDateTime.now()
                    .plusDays(if (isAccess) daysBeforeAccessTokenExpires else daysBeforeRefreshTokenExpires).toString()
                it[Authorizations.isAccess] = isAccess
            }

            insertStatement.resultedValues?.singleOrNull()?.let { resultRowToAuthorization(it) }
        }

    override suspend fun deleteTokensFromUser(userName: String): Boolean { //TODO: Suspicious return type
        newSuspendedTransaction(Dispatchers.IO, db) {
            Authorizations.deleteWhere { this.userName.eq(userName) }
        }
        return true
    }

    override suspend fun getAuthorization(token: String): Authorization? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Authorizations.select { Authorizations.token eq token }.map { resultRowToAuthorization(it) }.singleOrNull()
        }
    }

    override suspend fun deleteExpiredTokens(): Int {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Authorizations.deleteWhere { validUntil.less(OffsetDateTime.now().toString()) }
        }
    }

    override suspend fun state(): List<Authorization> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Authorizations.selectAll().map { resultRowToAuthorization(it) }
        }
    }
}