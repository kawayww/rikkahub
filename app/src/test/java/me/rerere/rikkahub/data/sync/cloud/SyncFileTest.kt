package me.rerere.rikkahub.data.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.path.createTempFile

class SyncFileTest {
    @Test
    fun `sha256 hash uses stable sync prefix and remote file path removes it`() {
        val file = createTempFile().toFile()
        try {
            file.writeText("hello")

            val hash = sha256Hash(file)

            assertEquals("sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash)
            assertEquals("files/2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", remoteFilePath(hash))
        } finally {
            file.delete()
        }
    }
}
