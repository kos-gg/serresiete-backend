package com.kos.acceptance

import com.kos.acceptance.fixtures.givenUser
import com.kos.acceptance.fixtures.givenView
import com.kos.activities.Activities
import com.kos.views.GetViewsResponse
import com.kos.views.Game
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewsE2ETest : AcceptanceTest() {

    // region GET /api/views/{id}

    @Test
    fun `owner can get their own view`() {
        val view = runBlocking { givenView(db, owner = "sanxei") }

        withApp { client ->
            val response = client.get("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.getOwnView))
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `user cannot get a view owned by someone else with only getOwnView`() {
        val view = runBlocking { givenView(db, owner = "bob") }

        withApp { client ->
            val response = client.get("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.getOwnView))
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `admin with getAnyView can get any user's view`() {
        val view = runBlocking { givenView(db, owner = "bob") }

        withApp { client ->
            val response = client.get("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.getAnyView))
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `getting a non-existent view returns 404`() = withApp { client ->
        val response = client.get("/api/views/does-not-exist") {
            bearerAuth(validJwt("sanxei", Activities.getAnyView))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // endregion

    // region GET /api/views

    @Test
    fun `getOwnViews user sees only their own views`() {
        runBlocking {
            givenView(db, owner = "sanxei")
            givenView(db, owner = "bob")
        }

        withApp { client ->
            val response = client.get("/api/views") {
                bearerAuth(validJwt("sanxei", Activities.getOwnViews))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<GetViewsResponse>()
            assertEquals(1, body.records.size)
            assertTrue(body.records.all { it.owner == "sanxei" })
        }
    }

    @Test
    fun `getAnyViews user sees all views`() {
        runBlocking {
            givenView(db, owner = "sanxei")
            givenView(db, owner = "bob")
        }

        withApp { client ->
            val response = client.get("/api/views") {
                bearerAuth(validJwt("sanxei", Activities.getAnyViews))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<GetViewsResponse>()
            assertEquals(2, body.records.size)
        }
    }

    @Test
    fun `listing views without permission returns 403`() = withApp { client ->
        val response = client.get("/api/views") {
            bearerAuth(validJwt("sanxei", Activities.getOwnView))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `filtering views by game returns only matching views`() {
        runBlocking {
            givenView(db, owner = "sanxei", game = Game.WOW)
            givenView(db, owner = "sanxei", game = Game.LOL)
        }

        withApp { client ->
            val response = client.get("/api/views?game=wow") {
                bearerAuth(validJwt("sanxei", Activities.getAnyViews))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<GetViewsResponse>()
            assertEquals(1, body.records.size)
            assertTrue(body.records.all { it.game == Game.WOW })
        }
    }

    @Test
    fun `filtering views by featured returns only featured views`() {
        runBlocking {
            givenView(db, owner = "sanxei", featured = true)
            givenView(db, owner = "sanxei", featured = false)
        }

        withApp { client ->
            val response = client.get("/api/views?featured=true") {
                bearerAuth(validJwt("sanxei", Activities.getAnyViews))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<GetViewsResponse>()
            assertEquals(1, body.records.size)
            assertTrue(body.records.all { it.featured })
        }
    }

    @Test
    fun `pagination limits the number of returned views`() {
        runBlocking {
            givenView(db, owner = "sanxei")
            givenView(db, owner = "sanxei")
            givenView(db, owner = "sanxei")
        }

        withApp { client ->
            val response = client.get("/api/views?page=1&limit=2") {
                bearerAuth(validJwt("sanxei", Activities.getAnyViews))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<GetViewsResponse>()
            assertEquals(2, body.records.size)
        }
    }

    @Test
    fun `invalid game query parameter returns 400`() = withApp { client ->
        val response = client.get("/api/views?game=invalid") {
            bearerAuth(validJwt("sanxei", Activities.getAnyViews))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // endregion

    // region POST /api/views

    @Test
    fun `user with createViews activity can create a view`() {
        runBlocking { givenUser(db, username = "sanxei") }

        withApp { client ->
            val response = client.post("/api/views") {
                bearerAuth(validJwt("sanxei", Activities.createViews))
                contentType(ContentType.Application.Json)
                setBody("""{"name":"My View","published":true,"entities":[],"game":"WOW","featured":false}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `user without createViews activity cannot create a view`() = withApp { client ->
        val response = client.post("/api/views") {
            bearerAuth(validJwt("sanxei", Activities.getOwnViews))
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My View","published":true,"entities":[],"game":"WOW","featured":false}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `user without featureView activity cannot create a featured view`() {
        runBlocking { givenUser(db, username = "sanxei") }

        withApp { client ->
            val response = client.post("/api/views") {
                bearerAuth(validJwt("sanxei", Activities.createViews))
                contentType(ContentType.Application.Json)
                setBody("""{"name":"My View","published":true,"entities":[],"game":"WOW","featured":true}""")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    // endregion

    // region PUT /api/views/{id}

    @Test
    fun `owner with editOwnView can edit their view`() {
        val view = runBlocking {
            givenUser(db, username = "sanxei")
            givenView(db, owner = "sanxei")
        }

        withApp { client ->
            val response = client.put("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.editOwnView))
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Updated","published":true,"entities":[],"game":"WOW","featured":false}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `user with editOwnView cannot edit another user's view`() {
        val view = runBlocking {
            givenUser(db, username = "sanxei")
            givenView(db, owner = "bob")
        }

        withApp { client ->
            val response = client.put("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.editOwnView))
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Updated","published":true,"entities":[],"game":"WOW","featured":false}""")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `admin with editAnyView can edit any view`() {
        val view = runBlocking {
            givenUser(db, username = "sanxei")
            givenView(db, owner = "bob")
        }

        withApp { client ->
            val response = client.put("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.editAnyView))
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Updated","published":true,"entities":[],"game":"WOW","featured":false}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `editing a non-existent view returns 404`() = withApp { client ->
        val response = client.put("/api/views/does-not-exist") {
            bearerAuth(validJwt("sanxei", Activities.editAnyView))
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated","published":true,"entities":[],"game":"WOW","featured":false}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // endregion

    // region PATCH /api/views/{id}

    @Test
    fun `owner with editOwnView can patch their view`() {
        val view = runBlocking {
            givenUser(db, username = "sanxei")
            givenView(db, owner = "sanxei")
        }

        withApp { client ->
            val response = client.patch("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.editOwnView))
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Patched","game":"WOW"}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `user with editOwnView cannot patch another user's view`() {
        val view = runBlocking {
            givenUser(db, username = "sanxei")
            givenView(db, owner = "bob")
        }

        withApp { client ->
            val response = client.patch("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.editOwnView))
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Patched","game":"WOW"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    // endregion

    // region DELETE /api/views/{id}

    @Test
    fun `owner with deleteOwnView can delete their view`() {
        val view = runBlocking { givenView(db, owner = "sanxei") }

        withApp { client ->
            val response = client.delete("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.deleteOwnView))
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `user with deleteOwnView cannot delete another user's view`() {
        val view = runBlocking { givenView(db, owner = "bob") }

        withApp { client ->
            val response = client.delete("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.deleteOwnView))
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `admin with deleteAnyView can delete any view`() {
        val view = runBlocking { givenView(db, owner = "bob") }

        withApp { client ->
            val response = client.delete("/api/views/${view.id}") {
                bearerAuth(validJwt("sanxei", Activities.deleteAnyView))
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `deleting a non-existent view returns 404`() = withApp { client ->
        val response = client.delete("/api/views/does-not-exist") {
            bearerAuth(validJwt("sanxei", Activities.deleteAnyView))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // endregion
}
