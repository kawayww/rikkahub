package me.rerere.rikkahub.data.sync.cloud

import kotlinx.serialization.Serializable

@Serializable
data class CloudSyncState(
    val deviceId: String = "",
    val lastPullAt: Long = 0L,
    val lastPushAt: Long = 0L,
    val lastManifestHash: String? = null,
    val lastRemoteObjectCount: Int = 0,
    val lastPulledConversationCount: Int = 0,
    val lastPulledSettingsCount: Int = 0,
    val lastPushedObjectCount: Int = 0,
    val objectStates: Map<String, LocalObjectState> = emptyMap(),
    val dirtyObjects: Set<String> = emptySet(),
    val lastError: String? = null,
) {
    fun markDirty(objectKey: String): CloudSyncState {
        return copy(dirtyObjects = dirtyObjects + objectKey)
    }

    fun clearDirty(objectKeys: Set<String>): CloudSyncState {
        if (objectKeys.isEmpty()) return this
        return copy(dirtyObjects = dirtyObjects - objectKeys)
    }

    fun updateObjectState(
        objectKey: String,
        entry: SyncManifestEntry,
    ): CloudSyncState {
        return copy(
            objectStates = objectStates + (
                objectKey to LocalObjectState(
                    version = entry.version,
                    updatedAt = entry.updatedAt,
                    hash = entry.hash,
                    deleted = entry.deleted,
                )
                )
        )
    }
}

@Serializable
data class LocalObjectState(
    val version: Long = 0L,
    val updatedAt: Long = 0L,
    val hash: String = "",
    val deleted: Boolean = false,
)
