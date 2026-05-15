package me.rerere.rikkahub.data.sync.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPullPolicyTest {
    @Test
    fun `manual sync pulls dirty settings from remote even when local state matches remote`() {
        val key = "settings:assistants"
        val entry = SyncManifestEntry(
            path = "settings/assistants.json",
            version = 3,
            updatedAt = 30,
            hash = "remote-hash",
            deviceId = "tablet",
        )
        val state = CloudSyncState(
            deviceId = "phone",
            objectStates = mapOf(
                key to LocalObjectState(
                    version = 3,
                    updatedAt = 30,
                    hash = "remote-hash",
                )
            ),
            dirtyObjects = setOf(key),
        )

        assertTrue(entry.shouldPullSettingsObject(state, key, SyncReason.Manual))
    }

    @Test
    fun `foreground sync does not pull dirty settings when local state matches remote`() {
        val key = "settings:assistants"
        val entry = SyncManifestEntry(
            path = "settings/assistants.json",
            version = 3,
            updatedAt = 30,
            hash = "remote-hash",
            deviceId = "tablet",
        )
        val state = CloudSyncState(
            deviceId = "phone",
            objectStates = mapOf(
                key to LocalObjectState(
                    version = 3,
                    updatedAt = 30,
                    hash = "remote-hash",
                )
            ),
            dirtyObjects = setOf(key),
        )

        assertFalse(entry.shouldPullSettingsObject(state, key, SyncReason.Foreground))
    }
}
