package me.rerere.rikkahub.data.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncManifestTest {
    @Test
    fun `merge keeps newer object entry`() {
        val local = SyncManifest(
            objects = mapOf(
                "conversation:a" to SyncManifestEntry(
                    path = "conversations/a.json",
                    version = 1,
                    updatedAt = 10,
                    hash = "old",
                    deviceId = "phone",
                )
            )
        )
        val remote = SyncManifest(
            objects = mapOf(
                "conversation:a" to SyncManifestEntry(
                    path = "conversations/a.json",
                    version = 2,
                    updatedAt = 20,
                    hash = "new",
                    deviceId = "tablet",
                )
            )
        )

        val merged = local.merge(remote)

        assertEquals("new", merged.objects.getValue("conversation:a").hash)
    }

    @Test
    fun `merge keeps deleted entry when it is newer`() {
        val live = SyncManifestEntry(
            path = "conversations/a.json",
            version = 2,
            updatedAt = 20,
            hash = "live",
            deviceId = "phone",
        )
        val deleted = SyncManifestEntry(
            path = "conversations/a.deleted.json",
            version = 3,
            updatedAt = 30,
            hash = "deleted",
            deviceId = "tablet",
            deleted = true,
        )

        val merged = SyncManifest(objects = mapOf("conversation:a" to live))
            .merge(SyncManifest(objects = mapOf("conversation:a" to deleted)))

        assertEquals(true, merged.objects.getValue("conversation:a").deleted)
        assertEquals("conversations/a.deleted.json", merged.objects.getValue("conversation:a").path)
    }

    @Test
    fun `dirty helpers add and clear object keys`() {
        val state = CloudSyncState(deviceId = "phone")
            .markDirty("conversation:a")
            .markDirty("settings:assistants")
            .clearDirty(setOf("conversation:a"))

        assertEquals(setOf("settings:assistants"), state.dirtyObjects)
    }
}
