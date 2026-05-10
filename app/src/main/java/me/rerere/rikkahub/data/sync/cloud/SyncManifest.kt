package me.rerere.rikkahub.data.sync.cloud

import kotlinx.serialization.Serializable

@Serializable
data class SyncManifest(
    val schemaVersion: Int = 1,
    val updatedAt: Long = 0L,
    val objects: Map<String, SyncManifestEntry> = emptyMap(),
) {
    fun merge(other: SyncManifest): SyncManifest {
        val mergedObjects = objects.toMutableMap()
        other.objects.forEach { (key, incoming) ->
            val current = mergedObjects[key]
            if (current == null || incoming.isNewerThan(current)) {
                mergedObjects[key] = incoming
            }
        }

        return copy(
            updatedAt = maxOf(updatedAt, other.updatedAt, mergedObjects.values.maxOfOrNull { it.updatedAt } ?: 0L),
            objects = mergedObjects.toMap(),
        )
    }
}

@Serializable
data class SyncManifestEntry(
    val path: String,
    val version: Long,
    val updatedAt: Long,
    val hash: String,
    val deviceId: String,
    val deleted: Boolean = false,
) {
    fun isNewerThan(other: SyncManifestEntry): Boolean {
        return version > other.version || (version == other.version && updatedAt > other.updatedAt)
    }
}

fun conversationObjectKey(conversationId: String): String = "conversation:$conversationId"

fun settingsObjectKey(kind: SettingsSyncKind): String = "settings:${kind.remoteName}"

fun fileObjectKey(hash: String): String = "file:$hash"
