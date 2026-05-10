package me.rerere.rikkahub.data.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncPullPolicyTest {
    @Test
    fun `dirty settings are not pulled from remote before upload`() {
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

        assertFalse(entry.shouldPullSettingsObject(state, key))
    }

    @Test
    fun `clean settings pull when remote is newer than local state`() {
        val key = "settings:assistants"
        val entry = SyncManifestEntry(
            path = "settings/assistants.json",
            version = 4,
            updatedAt = 40,
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
        )

        assertTrue(entry.shouldPullSettingsObject(state, key))
    }

    @Test
    fun `push version is above both local and remote versions`() {
        assertEquals(
            8,
            nextSyncVersion(
                local = LocalObjectState(version = 3),
                remote = SyncManifestEntry(
                    path = "settings/assistants.json",
                    version = 7,
                    updatedAt = 70,
                    hash = "remote",
                    deviceId = "tablet",
                ),
            ),
        )
    }
}
