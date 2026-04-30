package com.kos.acceptance

import com.kos.activities.Activities
import io.ktor.client.request.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ActivitiesE2ETest : AcceptanceTest() {

    @Test
    fun `authenticated user can reach activities endpoint`() = withApp { client ->
        val response = client.get("/api/activities") {
            bearerAuth(validJwt("sanxei", Activities.getAnyActivities))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `unauthenticated request is rejected with 401`() = withApp { client ->
        val response = client.get("/api/activities")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `expired token is rejected with 401`() = withApp { client ->
        val response = client.get("/api/activities") { bearerAuth(expiredJwt("sanxei")) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `refresh token cannot be used to access protected endpoints`() = withApp { client ->
        val response = client.get("/api/activities") { bearerAuth(refreshJwt("sanxei")) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
