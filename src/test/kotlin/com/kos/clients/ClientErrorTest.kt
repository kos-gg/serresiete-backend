package com.kos.clients

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientErrorTest {

    @Test
    fun `toSyncProcessingError maps a TimeoutError into a descriptive SyncProcessingError`() {
        val result = TimeoutError("Request timeout has expired").toSyncProcessingError("getRunDetails")

        assertEquals("TIMEOUT_ERROR: Timeout on getRunDetails: Request timeout has expired", result.error())
    }
}
