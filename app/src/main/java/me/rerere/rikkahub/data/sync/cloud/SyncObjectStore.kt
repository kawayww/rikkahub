package me.rerere.rikkahub.data.sync.cloud

import java.io.File

interface SyncObjectStore {
    suspend fun ensureReady(config: CloudSyncConfig)

    suspend fun read(config: CloudSyncConfig, path: String): ByteArray?

    suspend fun write(
        config: CloudSyncConfig,
        path: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
    )

    suspend fun writeFile(
        config: CloudSyncConfig,
        path: String,
        file: File,
        contentType: String = "application/octet-stream",
    )

    suspend fun downloadFile(
        config: CloudSyncConfig,
        path: String,
        target: File,
    ): Boolean

    suspend fun delete(config: CloudSyncConfig, path: String)
}
