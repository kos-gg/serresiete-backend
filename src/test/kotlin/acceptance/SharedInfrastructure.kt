package acceptance

import com.kos.common.JWTConfig
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object SharedInfrastructure {

    val postgres: EmbeddedPostgres = EmbeddedPostgres.start()
    val db: Database = Database.connect(postgres.postgresDatabase)
    val jwtConfig = JWTConfig(issuer = "test-issuer", secret = "test-secret-at-least-32-chars!!")

    private val flyway: Flyway = Flyway.configure()
        .locations("db/migration/prod")
        .dataSource(postgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    private val app: TestApplication = TestApplication {
        application { testModule(db, jwtConfig) }
    }

    val client: HttpClient = runBlocking {
        app.start()
        app.createClient { install(ContentNegotiation) { json() } }
    }

    fun resetDatabase() {
        flyway.clean()
        flyway.migrate()
    }
}
