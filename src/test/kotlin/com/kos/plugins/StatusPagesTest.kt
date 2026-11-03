package com.kos.plugins

import com.kos.common.error.TooMuchViews
import com.kos.common.error.respondWithHandledError
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StatusPagesTest {

    @Serializable
    data class Body(val value: String)

    private fun withApp(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            routing {
                get("/boom") { error("secret internal detail") }
                post("/body") { call.respond(call.receive<Body>()) }
                get("/handled") { call.respondWithHandledError(TooMuchViews) }
            }
        }
        test()
    }

    @Test
    fun `an unexpected exception becomes a generic internal error without leaking its message`() = withApp {
        val response = client.get("/boom")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ContentType("application", "problem+json"), response.contentType()?.withoutParameters())
        assertEquals("""{"status":500,"code":"INTERNAL_ERROR","title":"Internal error"}""", response.bodyAsText())
        assertFalse(response.bodyAsText().contains("secret"))
    }

    @Test
    fun `a malformed request body is still a bad request`() = withApp {
        val response = client.post("/body") {
            contentType(ContentType.Application.Json)
            setBody("{not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"status":400,"code":"BAD_REQUEST","title":"Bad Request"}""", response.bodyAsText())
    }

    @Test
    fun `a controller error is answered as a problem with its code`() = withApp {
        val response = client.get("/handled")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ContentType("application", "problem+json"), response.contentType()?.withoutParameters())
        assertEquals("""{"status":400,"code":"TOO_MANY_VIEWS","title":"Too many views"}""", response.bodyAsText())
    }
}
