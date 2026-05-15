package me.rerere.rikkahub.data.sync.webdav

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class WebDavObjectStoreTest {
    @Test
    fun `missing WebDAV resources become null`() {
        val result = Result.failure<ByteArray>(
            WebDavException(
                message = "missing",
                statusCode = 404,
                responseBody = "",
            )
        )

        assertNull(result.getOrNullIfMissing())
    }

    @Test
    fun `successful WebDAV resources are returned`() {
        val bytes = "manifest".encodeToByteArray()

        assertArrayEquals(bytes, Result.success(bytes).getOrNullIfMissing())
    }

    @Test
    fun `non missing WebDAV errors are thrown`() {
        val result = Result.failure<ByteArray>(
            WebDavException(
                message = "server error",
                statusCode = 500,
                responseBody = "error",
            )
        )

        try {
            result.getOrNullIfMissing()
            fail("Expected WebDavException")
        } catch (error: WebDavException) {
            assertEquals(500, error.statusCode)
        }
    }
}
