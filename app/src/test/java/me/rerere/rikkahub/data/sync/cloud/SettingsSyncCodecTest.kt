package me.rerere.rikkahub.data.sync.cloud

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsSyncCodecTest {
    @Test
    fun `settings payloads do not include provider or backup secrets`() {
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(
                    id = Uuid.parse("11111111-1111-1111-1111-111111111111"),
                    apiKey = "openai-secret",
                )
            ),
            webDavConfig = WebDavConfig(
                username = "webdav-user",
                password = "webdav-secret",
            ),
            s3Config = S3Config(
                accessKeyId = "s3-access",
                secretAccessKey = "s3-secret",
            ),
        )

        val encoded = JsonInstant.encodeToString(
            buildSettingsSyncEnvelopes(
                settings = settings,
                kinds = SettingsSyncKind.entries.toSet(),
                updatedAt = 10,
                deviceId = "phone",
            )
        )

        assertFalse(encoded.contains("openai-secret"))
        assertFalse(encoded.contains("webdav-secret"))
        assertFalse(encoded.contains("s3-secret"))
        assertFalse(encoded.contains("s3-access"))
    }

    @Test
    fun `changed settings kinds detect assistants lorebooks and model selection`() {
        val assistant = Assistant(
            id = Uuid.parse("22222222-2222-2222-2222-222222222222"),
            name = "assistant",
        )
        val base = Settings(
            assistants = listOf(assistant),
            lorebooks = emptyList(),
            chatModelId = Uuid.parse("33333333-3333-3333-3333-333333333333"),
        )
        val changed = base.copy(
            assistants = listOf(assistant.copy(name = "renamed")),
            lorebooks = listOf(
                Lorebook(
                    id = Uuid.parse("44444444-4444-4444-4444-444444444444"),
                    name = "lore",
                )
            ),
            chatModelId = Uuid.parse("55555555-5555-5555-5555-555555555555"),
        )

        val kinds = changedSettingsKinds(base, changed)

        assertEquals(
            setOf(
                SettingsSyncKind.Assistants,
                SettingsSyncKind.Lorebooks,
                SettingsSyncKind.ModelSelection,
            ),
            kinds,
        )
    }

    @Test
    fun `settings envelope applies only its own payload kind`() {
        val base = Settings(
            assistants = listOf(Assistant(name = "old")),
            lorebooks = emptyList(),
        )
        val incoming = Settings(
            assistants = listOf(Assistant(name = "new")),
            lorebooks = listOf(Lorebook(name = "synced")),
        )
        val envelope = buildSettingsSyncEnvelope(
            kind = SettingsSyncKind.Lorebooks,
            settings = incoming,
            updatedAt = 10,
            deviceId = "phone",
        )

        val applied = applySettingsSyncEnvelope(base, envelope)

        assertEquals("old", applied.assistants.single().name)
        assertEquals("synced", applied.lorebooks.single().name)
    }

    @Test
    fun `bootstrap upload detects old state that claims remote settings but local content differs`() {
        val remoteSettings = Settings(assistants = listOf(Assistant(name = "remote")))
        val localSettings = Settings(assistants = listOf(Assistant(name = "tablet")))
        val remoteEntry = remoteSettingsEntry(remoteSettings)
        val key = settingsObjectKey(SettingsSyncKind.Assistants)

        val kinds = bootstrapSettingsKindsToUpload(
            settings = localSettings,
            state = CloudSyncState(
                objectStates = mapOf(
                    key to LocalObjectState(
                        version = remoteEntry.version,
                        updatedAt = remoteEntry.updatedAt,
                        hash = remoteEntry.hash,
                    )
                )
            ),
            remoteManifest = SyncManifest(objects = mapOf(key to remoteEntry)),
        )

        assertEquals(setOf(SettingsSyncKind.Assistants), kinds)
    }

    @Test
    fun `bootstrap upload skips settings when local content already matches remote`() {
        val settings = Settings(assistants = listOf(Assistant(name = "tablet")))
        val remoteEntry = remoteSettingsEntry(settings)
        val key = settingsObjectKey(SettingsSyncKind.Assistants)

        val kinds = bootstrapSettingsKindsToUpload(
            settings = settings,
            state = CloudSyncState(
                objectStates = mapOf(
                    key to LocalObjectState(
                        version = remoteEntry.version,
                        updatedAt = remoteEntry.updatedAt,
                        hash = remoteEntry.hash,
                    )
                )
            ),
            remoteManifest = SyncManifest(objects = mapOf(key to remoteEntry)),
        )

        assertTrue(kinds.isEmpty())
    }

    @Test
    fun `bootstrap upload does not override a remote settings object newer than local state`() {
        val localSettings = Settings(assistants = listOf(Assistant(name = "tablet")))
        val oldEntry = remoteSettingsEntry(Settings(assistants = listOf(Assistant(name = "old"))), version = 1)
        val newEntry = remoteSettingsEntry(Settings(assistants = listOf(Assistant(name = "remote"))), version = 2)
        val key = settingsObjectKey(SettingsSyncKind.Assistants)

        val kinds = bootstrapSettingsKindsToUpload(
            settings = localSettings,
            state = CloudSyncState(
                objectStates = mapOf(
                    key to LocalObjectState(
                        version = oldEntry.version,
                        updatedAt = oldEntry.updatedAt,
                        hash = oldEntry.hash,
                    )
                )
            ),
            remoteManifest = SyncManifest(objects = mapOf(key to newEntry)),
        )

        assertTrue(kinds.isEmpty())
    }

    private fun remoteSettingsEntry(
        settings: Settings,
        version: Long = 1,
    ): SyncManifestEntry {
        val updatedAt = version * 10
        val deviceId = "remote-device"
        val envelope = buildSettingsSyncEnvelope(
            kind = SettingsSyncKind.Assistants,
            settings = settings,
            updatedAt = updatedAt,
            deviceId = deviceId,
        )
        val hash = sha256Hash(JsonInstant.encodeToString(envelope).encodeToByteArray())
        return SyncManifestEntry(
            path = SettingsSyncKind.Assistants.path,
            version = version,
            updatedAt = updatedAt,
            hash = hash,
            deviceId = deviceId,
        )
    }
}
