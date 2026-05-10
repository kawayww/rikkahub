package me.rerere.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageTest {
    @Test
    fun `cache hit rate should be null when prompt tokens are zero`() {
        val usage = TokenUsage()

        assertNull(usage.cacheHitRate())
    }

    @Test
    fun `cache hit rate should be computed from cached and prompt tokens`() {
        val usage = TokenUsage(
            promptTokens = 200,
            cachedTokens = 50,
        )

        assertEquals(0.25, usage.cacheHitRate(), 0.0001)
    }

    @Test
    fun `cache hit rate should reach one when all prompt tokens are cached`() {
        val usage = TokenUsage(
            promptTokens = 128,
            cachedTokens = 128,
        )

        assertEquals(1.0, usage.cacheHitRate(), 0.0001)
    }
}
