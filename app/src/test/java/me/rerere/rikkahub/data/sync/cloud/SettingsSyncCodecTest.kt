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
}
