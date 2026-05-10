package me.rerere.rikkahub.data.sync.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANTS
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.DEFAULT_MODE_INJECTIONS
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

@Serializable
data class SettingsSyncEnvelope(
    val schemaVersion: Int = 1,
    val objectKey: String,
    val path: String,
    val kind: SettingsSyncKind,
    val updatedAt: Long,
    val deviceId: String,
    val payload: JsonElement,
)

@Serializable
data class AssistantsSettingsPayload(
    val assistants: List<Assistant>,
    val assistantId: Uuid,
)

@Serializable
data class AssistantTagsSettingsPayload(
    val tags: List<Tag>,
)

@Serializable
data class PromptInjectionsSettingsPayload(
    val modeInjections: List<PromptInjection.ModeInjection>,
)

@Serializable
data class LorebooksSettingsPayload(
    val lorebooks: List<Lorebook>,
)

@Serializable
data class QuickMessagesSettingsPayload(
    val quickMessages: List<QuickMessage>,
)

@Serializable
data class ModelSelectionSettingsPayload(
    val enableWebSearch: Boolean,
    val favoriteModels: List<Uuid>,
    val chatModelId: Uuid,
    val titleModelId: Uuid,
    val conversationSummaryModelId: Uuid,
    val imageGenerationModelId: Uuid,
    val titlePrompt: String,
    val translateModeId: Uuid,
    val translatePrompt: String,
    val translateThinkingBudget: Int,
    val suggestionModelId: Uuid,
    val suggestionPrompt: String,
    val ocrModelId: Uuid,
    val ocrPrompt: String,
    val compressModelId: Uuid,
    val compressPrompt: String,
)

fun buildSettingsSyncEnvelopes(
    settings: Settings,
    kinds: Set<SettingsSyncKind>,
    updatedAt: Long,
    deviceId: String,
    json: Json = JsonInstant,
): List<SettingsSyncEnvelope> {
    return kinds
        .sortedBy { it.ordinal }
        .map { kind ->
            buildSettingsSyncEnvelope(
                kind = kind,
                settings = settings,
                updatedAt = updatedAt,
                deviceId = deviceId,
                json = json,
            )
        }
}

fun buildSettingsSyncEnvelope(
    kind: SettingsSyncKind,
    settings: Settings,
    updatedAt: Long,
    deviceId: String,
    json: Json = JsonInstant,
): SettingsSyncEnvelope {
    return SettingsSyncEnvelope(
        objectKey = settingsObjectKey(kind),
        path = kind.path,
        kind = kind,
        updatedAt = updatedAt,
        deviceId = deviceId,
        payload = settings.payloadFor(kind, json),
    )
}

fun applySettingsSyncEnvelope(
    settings: Settings,
    envelope: SettingsSyncEnvelope,
    json: Json = JsonInstant,
): Settings {
    return when (envelope.kind) {
        SettingsSyncKind.Assistants -> {
            val payload = json.decodeFromJsonElement<AssistantsSettingsPayload>(envelope.payload)
            settings.copy(
                assistants = payload.assistants,
                assistantId = payload.assistantId,
            )
        }

        SettingsSyncKind.AssistantTags -> {
            val payload = json.decodeFromJsonElement<AssistantTagsSettingsPayload>(envelope.payload)
            settings.copy(assistantTags = payload.tags)
        }

        SettingsSyncKind.PromptInjections -> {
            val payload = json.decodeFromJsonElement<PromptInjectionsSettingsPayload>(envelope.payload)
            settings.copy(modeInjections = payload.modeInjections)
        }

        SettingsSyncKind.Lorebooks -> {
            val payload = json.decodeFromJsonElement<LorebooksSettingsPayload>(envelope.payload)
            settings.copy(lorebooks = payload.lorebooks)
        }

        SettingsSyncKind.QuickMessages -> {
            val payload = json.decodeFromJsonElement<QuickMessagesSettingsPayload>(envelope.payload)
            settings.copy(quickMessages = payload.quickMessages)
        }

        SettingsSyncKind.ModelSelection -> {
            val payload = json.decodeFromJsonElement<ModelSelectionSettingsPayload>(envelope.payload)
            settings.copy(
                enableWebSearch = payload.enableWebSearch,
                favoriteModels = payload.favoriteModels,
                chatModelId = payload.chatModelId,
                titleModelId = payload.titleModelId,
                conversationSummaryModelId = payload.conversationSummaryModelId,
                imageGenerationModelId = payload.imageGenerationModelId,
                titlePrompt = payload.titlePrompt,
                translateModeId = payload.translateModeId,
                translatePrompt = payload.translatePrompt,
                translateThinkingBudget = payload.translateThinkingBudget,
                suggestionModelId = payload.suggestionModelId,
                suggestionPrompt = payload.suggestionPrompt,
                ocrModelId = payload.ocrModelId,
                ocrPrompt = payload.ocrPrompt,
                compressModelId = payload.compressModelId,
                compressPrompt = payload.compressPrompt,
            )
        }
    }
}

fun changedSettingsKinds(
    old: Settings,
    new: Settings,
): Set<SettingsSyncKind> {
    return buildSet {
        if (old.assistants != new.assistants || old.assistantId != new.assistantId) {
            add(SettingsSyncKind.Assistants)
        }
        if (old.assistantTags != new.assistantTags) {
            add(SettingsSyncKind.AssistantTags)
        }
        if (old.modeInjections != new.modeInjections) {
            add(SettingsSyncKind.PromptInjections)
        }
        if (old.lorebooks != new.lorebooks) {
            add(SettingsSyncKind.Lorebooks)
        }
        if (old.quickMessages != new.quickMessages) {
            add(SettingsSyncKind.QuickMessages)
        }
        if (old.modelSelectionPayload() != new.modelSelectionPayload()) {
            add(SettingsSyncKind.ModelSelection)
        }
    }
}

fun bootstrapSettingsKinds(settings: Settings): Set<SettingsSyncKind> {
    return buildSet {
        if (settings.assistants != DEFAULT_ASSISTANTS || settings.assistantId != DEFAULT_ASSISTANT_ID) {
            add(SettingsSyncKind.Assistants)
        }
        if (settings.assistantTags.isNotEmpty()) {
            add(SettingsSyncKind.AssistantTags)
        }
        if (settings.modeInjections != DEFAULT_MODE_INJECTIONS) {
            add(SettingsSyncKind.PromptInjections)
        }
        if (settings.lorebooks.isNotEmpty()) {
            add(SettingsSyncKind.Lorebooks)
        }
        if (settings.quickMessages.isNotEmpty()) {
            add(SettingsSyncKind.QuickMessages)
        }
        if (settings.hasCustomModelSelection()) {
            add(SettingsSyncKind.ModelSelection)
        }
    }
}

fun bootstrapSettingsKindsToUpload(
    settings: Settings,
    state: CloudSyncState,
    remoteManifest: SyncManifest,
    json: Json = JsonInstant,
): Set<SettingsSyncKind> {
    return bootstrapSettingsKinds(settings).filterTo(mutableSetOf()) { kind ->
        val key = settingsObjectKey(kind)
        val remote = remoteManifest.objects[key] ?: return@filterTo true
        val local = state.objectStates[key] ?: return@filterTo true
        val localStateMatchesRemote = local.version == remote.version && local.hash == remote.hash
        localStateMatchesRemote && settingsEnvelopeHash(kind, settings, remote, json) != remote.hash
    }
}

private fun Settings.payloadFor(
    kind: SettingsSyncKind,
    json: Json,
): JsonElement {
    return when (kind) {
        SettingsSyncKind.Assistants -> json.encodeToJsonElement(
            AssistantsSettingsPayload(
                assistants = assistants,
                assistantId = assistantId,
            )
        )

        SettingsSyncKind.AssistantTags -> json.encodeToJsonElement(
            AssistantTagsSettingsPayload(tags = assistantTags)
        )

        SettingsSyncKind.PromptInjections -> json.encodeToJsonElement(
            PromptInjectionsSettingsPayload(modeInjections = modeInjections)
        )

        SettingsSyncKind.Lorebooks -> json.encodeToJsonElement(
            LorebooksSettingsPayload(lorebooks = lorebooks)
        )

        SettingsSyncKind.QuickMessages -> json.encodeToJsonElement(
            QuickMessagesSettingsPayload(quickMessages = quickMessages)
        )

        SettingsSyncKind.ModelSelection -> json.encodeToJsonElement(modelSelectionPayload())
    }
}

private fun Settings.modelSelectionPayload(): ModelSelectionSettingsPayload {
    return ModelSelectionSettingsPayload(
        enableWebSearch = enableWebSearch,
        favoriteModels = favoriteModels,
        chatModelId = chatModelId,
        titleModelId = titleModelId,
        conversationSummaryModelId = conversationSummaryModelId,
        imageGenerationModelId = imageGenerationModelId,
        titlePrompt = titlePrompt,
        translateModeId = translateModeId,
        translatePrompt = translatePrompt,
        translateThinkingBudget = translateThinkingBudget,
        suggestionModelId = suggestionModelId,
        suggestionPrompt = suggestionPrompt,
        ocrModelId = ocrModelId,
        ocrPrompt = ocrPrompt,
        compressModelId = compressModelId,
        compressPrompt = compressPrompt,
    )
}

private fun Settings.hasCustomModelSelection(): Boolean {
    return enableWebSearch ||
        favoriteModels.isNotEmpty() ||
        titlePrompt != DEFAULT_TITLE_PROMPT ||
        translatePrompt != DEFAULT_TRANSLATION_PROMPT ||
        translateThinkingBudget != 0 ||
        suggestionPrompt != DEFAULT_SUGGESTION_PROMPT ||
        ocrPrompt != DEFAULT_OCR_PROMPT ||
        compressPrompt != DEFAULT_COMPRESS_PROMPT
}

private fun settingsEnvelopeHash(
    kind: SettingsSyncKind,
    settings: Settings,
    remote: SyncManifestEntry,
    json: Json,
): String {
    val envelope = buildSettingsSyncEnvelope(
        kind = kind,
        settings = settings,
        updatedAt = remote.updatedAt,
        deviceId = remote.deviceId,
        json = json,
    )
    return sha256Hash(json.encodeToString(envelope).encodeToByteArray())
}
