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
    fun `non empty remote manifest bootstraps local conversations missing from remote`() {
        val dirtyObjects = initialSyncDirtyObjects(
            remoteManifest = SyncManifest(
                objects = mapOf(
                    "settings:assistants" to SyncManifestEntry(
                        path = "settings/assistants.json",
                        version = 1,
                        updatedAt = 10,
                        hash = "hash",
                        deviceId = "tablet",
                    )
                )
            ),
            state = CloudSyncState(deviceId = "phone"),
            localConversationIds = listOf("phone-local"),
            settingsKinds = setOf(SettingsSyncKind.Assistants),
        )

        assertEquals(setOf("conversation:phone-local"), dirtyObjects)
    }

    @Test
    fun `untracked customized settings are uploaded even when remote object exists`() {
        val dirtyObjects = initialSyncDirtyObjects(
            remoteManifest = SyncManifest(
                objects = mapOf(
                    "settings:assistants" to SyncManifestEntry(
                        path = "settings/assistants.json",
                        version = 1,
                        updatedAt = 10,
                        hash = "old-remote-hash",
                        deviceId = "phone",
                    )
                )
            ),
            state = CloudSyncState(deviceId = "tablet"),
            localConversationIds = emptyList(),
            settingsKinds = setOf(SettingsSyncKind.Assistants),
            settingsKindsToUpload = setOf(SettingsSyncKind.Assistants),
        )

        assertEquals(setOf("settings:assistants"), dirtyObjects)
    }

    @Test
    fun `tracked settings do not bootstrap over remote object unless precomputed for upload`() {
        val dirtyObjects = initialSyncDirtyObjects(
            remoteManifest = SyncManifest(
                objects = mapOf(
                    "settings:assistants" to SyncManifestEntry(
                        path = "settings/assistants.json",
                        version = 2,
                        updatedAt = 20,
                        hash = "remote-hash",
                        deviceId = "phone",
                    )
                )
            ),
            state = CloudSyncState(
                deviceId = "tablet",
                objectStates = mapOf(
                    "settings:assistants" to LocalObjectState(
                        version = 1,
                        updatedAt = 10,
                        hash = "old-local-hash",
                    )
                )
            ),
            localConversationIds = emptyList(),
            settingsKinds = setOf(SettingsSyncKind.Assistants),
            settingsKindsToUpload = emptySet(),
        )

        assertEquals(emptySet<String>(), dirtyObjects)
    }

    @Test
    fun `old local object state does not prevent uploading conversations missing from current remote`() {
        val dirtyObjects = initialSyncDirtyObjects(
            remoteManifest = SyncManifest(),
            state = CloudSyncState(
                deviceId = "tablet",
                objectStates = mapOf(
                    "conversation:a" to LocalObjectState(
                        version = 3,
                        updatedAt = 30,
                        hash = "old-remote-hash",
                    )
                ),
            ),
            localConversationIds = listOf("a"),
            settingsKinds = emptySet(),
        )

        assertEquals(setOf("conversation:a"), dirtyObjects)
    }
}
