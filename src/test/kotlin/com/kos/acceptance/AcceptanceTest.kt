package com.kos.acceptance

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kos.auth.TokenMode
import com.kos.common.JWTConfig
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AcceptanceTest {

    private val postgres: EmbeddedPostgres = EmbeddedPostgres.start()
    protected val db: Database = Database.connect(postgres.postgresDatabase)
    protected val jwtConfig = JWTConfig(issuer = "test-issuer", secret = "test-secret-at-least-32-chars!!")

    private val flyway: Flyway = Flyway.configure()
        .locations("db/migration/prod")
        .dataSource(postgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    @BeforeEach
    fun resetDatabase() {
        flyway.clean()
        flyway.migrate()
    }

    protected fun validJwt(username: String, vararg activities: String): String =
        JWT.create()
            .withIssuer(jwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("mode", TokenMode.ACCESS.toString())
            .withClaim("activities", activities.toList())
            .withExpiresAt(Date.from(OffsetDateTime.now().plusHours(1).toInstant()))
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    protected fun expiredJwt(username: String, vararg activities: String): String =
        JWT.create()
            .withIssuer(jwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("mode", TokenMode.ACCESS.toString())
            .withClaim("activities", activities.toList())
            .withExpiresAt(Date.from(OffsetDateTime.now().minusHours(1).toInstant()))
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    protected fun refreshJwt(username: String): String =
        JWT.create()
            .withIssuer(jwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("mode", TokenMode.REFRESH.toString())
            .withClaim("activities", emptyList<String>())
            .withExpiresAt(Date.from(OffsetDateTime.now().plusDays(30).toInstant()))
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    protected fun withApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
        testApplication {
            application { testModule(db, jwtConfig) }
            val client = createClient {
                install(ContentNegotiation) { json() }
            }
            block(client)
        }
    }
}
