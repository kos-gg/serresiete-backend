package com.kos.entities

import com.kos.clients.TimeoutError
import kotlin.test.Test
import kotlin.test.assertEquals

class PartialFailureTest {

    @Test
    fun `a failed third party call describes the entity and the original client error`() {
        val result = ThirdPartyCallFailed(42, TimeoutError("Request timeout has expired"))

        assertEquals("Entity 42: TimeoutError(message=Request timeout has expired)", result.error())
    }
}
