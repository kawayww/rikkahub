package me.rerere.rikkahub.data.sync.cloud

import java.io.File
import java.security.MessageDigest

private const val SHA256_PREFIX = "sha256:"

fun sha256Hash(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    file.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return SHA256_PREFIX + digest.digest().toHexString()
}

fun sha256Hash(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return SHA256_PREFIX + digest.digest(bytes).toHexString()
}

fun remoteFilePath(hash: String): String {
    return "files/${hash.removePrefix(SHA256_PREFIX)}"
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = 16).padStart(2, '0')
    }
}
