package com.kos.common

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

object DatabaseFactory {

    private val url = System.getenv("POSTGRES_URL") ?: "jdbc:h2:file:./build/db"
    private val user = System.getenv("POSTGRES_USER") ?: ""
    private val password = System.getenv("POSTGRES_PASSWORD") ?: ""
    private val driver = System.getenv("POSTGRES_DRIVER") ?: "org.h2.Driver"
    private val maxPoolSize = System.getenv("POSTGRES_MAX_POOL_SIZE")?.toInt() ?: 10

    fun pooledDatabase(): Database {

        fun hikari(): DataSource {
            val config = HikariConfig()
            config.driverClassName = driver
            config.jdbcUrl = url
            config.username = user
            config.password = password
            config.maximumPoolSize = maxPoolSize
            config.isAutoCommit = false
            config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            config.validate()
            return HikariDataSource(config)
        }

        val flyway = Flyway
            .configure()
            .locations("db/migration/prod")
            .validateMigrationNaming(true)
            .dataSource(url, user, password)
            .load()

        flyway.migrate()
        return Database.connect(hikari())
    }
}