package com.kos.views

import com.kos.common.error.InvalidQueryParameter
import com.kos.common.getLeftOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewsRoutesTest {

    @Test
    fun `parsing a missing query param returns null`() {
        val result = parsePositiveIntQueryParam("page", null)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `parsing a valid positive integer returns it`() {
        val result = parsePositiveIntQueryParam("page", "5")
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun `parsing zero is rejected as an invalid query param`() {
        val result = parsePositiveIntQueryParam("page", "0")
        assertTrue(result.isLeft())
        assertEquals("invalid query param[page]: 0\nallowed values: [a positive integer]", result.getLeftOrNull()?.message)
    }

    @Test
    fun `parsing a negative number is rejected as an invalid query param`() {
        val result = parsePositiveIntQueryParam("limit", "-3")
        assertTrue(result.isLeft())
        assertEquals("invalid query param[limit]: -3\nallowed values: [a positive integer]", result.getLeftOrNull()?.message)
    }

    @Test
    fun `parsing a non-numeric value is rejected as an invalid query param`() {
        val result = parsePositiveIntQueryParam("page", "abc")
        assertTrue(result.isLeft())
        assertEquals("invalid query param[page]: abc\nallowed values: [a positive integer]", result.getLeftOrNull()?.message)
    }
}
