package me.rerere.rikkahub.data.sync.webdav

import io.ktor.client.HttpClient
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.cloud.CloudSyncConfig
import me.rerere.rikkahub.data.sync.cloud.SyncObjectStore
import java.io.File

class WebDavObjectStore(
    private val httpClient: HttpClient,
) : SyncObjectStore {
    override suspend fun ensureReady(config: CloudSyncConfig) {
        val client = client(config)
        client.ensureCollectionExists().getOrThrow()
        client.ensureCollectionExists("conversations").getOrThrow()
        client.ensureCollectionExists("settings").getOrThrow()
        client.ensureCollectionExists("files").getOrThrow()
    }

    override suspend fun read(
        config: CloudSyncConfig,
        path: String,
    ): ByteArray? {
        val client = client(config)
        if (!client.exists(path)) return null
        return client.get(path).getOrThrow()
    }

    override suspend fun write(
        config: CloudSyncConfig,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        client(config).put(path, bytes, contentType).getOrThrow()
    }

    override suspend fun writeFile(
        config: CloudSyncConfig,
        path: String,
        file: File,
        contentType: String,
    ) {
        client(config).put(path, file, contentType).getOrThrow()
    }

    override suspend fun downloadFile(
        config: CloudSyncConfig,
        path: String,
        target: File,
    ): Boolean {
        val client = client(config)
        if (!client.exists(path)) return false
        client.downloadToFile(path, target).getOrThrow()
        return true
    }

    override suspend fun delete(
        config: CloudSyncConfig,
        path: String,
    ) {
        val client = client(config)
        if (client.exists(path)) {
            client.delete(path).getOrThrow()
        }
    }

    private fun client(config: CloudSyncConfig): WebDavClient {
        return WebDavClient(
            config = WebDavConfig(
                url = config.url,
                username = config.username,
                password = config.password,
                path = config.path,
            ),
            httpClient = httpClient,
        )
    }
}
