package me.rerere.rikkahub.data.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialSyncBootstrapTest {
    @Test
    fun `empty remote manifest bootstraps local conversations and syncable settings`() {
        val stateAfterOldNoOpSync = CloudSyncState(
            deviceId = "tablet",
            lastPullAt = 100,
            lastPushAt = 100,
        )

        val dirtyObjects = initialSyncDirtyObjects(
            remoteManifest = SyncManifest(),
            state = stateAfterOldNoOpSync,
            localConversationIds = listOf("a", "b"),
        )

        assertEquals(
            setOf(
                "conversation:a",
                "conversation:b",
                "settings:assistants",
                "settings:assistant-tags",
                "settings:prompt-injections",
                "settings:lorebooks",
                "settings:quick-messages",
                "settings:model-selection",
            ),
            dirtyObjects,
        )
    }

    @Test
    fun `non empty remote manifest does not bootstrap local settings over remote data`() {
        val dirtyObjects = initialSyncDirtyObjects(
            remoteManifest = SyncManifest(
                objects = mapOf(
                    "conversation:a" to SyncManifestEntry(
                        path = "conversations/a.json",
                        version = 1,
                        updatedAt = 10,
                        hash = "hash",
                        deviceId = "tablet",
                    )
                )
            ),
            state = CloudSyncState(deviceId = "phone"),
            localConversationIds = listOf("phone-local"),
        )

        assertEquals(emptySet<String>(), dirtyObjects)
    }
}
