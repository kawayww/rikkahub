package me.rerere.rikkahub.data.sync.cloud

import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationChange
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.io.File
import java.time.Instant
import kotlin.uuid.Uuid

private const val MANIFEST_PATH = "manifest.json"
private const val JSON_CONTENT_TYPE = "application/json"

class CloudSyncManager(
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val filesManager: FilesManager,
    private val objectStore: SyncObjectStore,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(CloudSyncStatus.Idle)
    private var suppressSettingsDirty = false
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) {
            appScope.launchSync(SyncReason.Foreground)
        }
    }

    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    init {
        settingsStore.addUpdateListener { old, new ->
            if (!suppressSettingsDirty) {
                val changedKinds = changedSettingsKinds(old, new)
                if (changedKinds.isNotEmpty()) {
                    markSettingsDirty(changedKinds)
                }
            }
        }
        conversationRepository.addChangeListener { change ->
            when (change) {
                is ConversationChange.Upsert -> markConversationDirty(change.conversationId)
                is ConversationChange.Delete -> markConversationDeleted(change.conversationId)
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        appScope.launchSyncAfterSettingsReady()
    }

    suspend fun testConnection(config: CloudSyncConfig) {
        objectStore.ensureReady(config)
    }

    suspend fun markConversationDirty(conversationId: Uuid) {
        val key = conversationObjectKey(conversationId.toString())
        if (key in settingsStore.settingsFlow.value.cloudSyncState.dirtyObjects) return
        markDirty(key)
    }

    suspend fun markConversationDeleted(conversationId: Uuid) {
        val key = conversationObjectKey(conversationId.toString())
        val now = nowMillis()
        updateCloudState { state ->
            val current = state.objectStates[key]
            val entry = SyncManifestEntry(
                path = deletedConversationPath(conversationId.toString()),
                version = (current?.version ?: 0L) + 1L,
                updatedAt = now,
                hash = "deleted:$now",
                deviceId = state.deviceId,
                deleted = true,
            )
            state.updateObjectState(key, entry).markDirty(key)
        }
    }

    suspend fun markSettingsDirty(kinds: Set<SettingsSyncKind>) {
        if (kinds.isEmpty()) return
        val dirtyObjects = settingsStore.settingsFlow.value.cloudSyncState.dirtyObjects
        val missingKinds = kinds.filter { settingsObjectKey(it) !in dirtyObjects }
        if (missingKinds.isEmpty()) return
        updateCloudState { state ->
            missingKinds.fold(state) { acc, kind -> acc.markDirty(settingsObjectKey(kind)) }
        }
    }

    suspend fun syncNow(reason: SyncReason = SyncReason.Manual) {
        mutex.withLock {
            val initialSettings = settingsStore.settingsFlow.value
            val config = initialSettings.cloudSyncConfig
            if (!config.enabled || config.url.isBlank()) {
                _status.value = CloudSyncStatus.Idle
                return
            }

            _status.value = CloudSyncStatus.Syncing
            runCatching {
                objectStore.ensureReady(config)
                val state = ensureDeviceState(initialSettings.cloudSyncState)
                if (state != initialSettings.cloudSyncState) {
                    settingsStore.update { it.copy(cloudSyncState = state) }
                }

                var manifest = readManifest(config)
                val bootstrapDirtyObjects = initialSyncDirtyObjects(
                    remoteManifest = manifest,
                    state = state,
                    localConversationIds = conversationRepository.getAllConversations()
                        .map { it.id.toString() },
                    settingsKindsToUpload = bootstrapSettingsKindsToUpload(
                        settings = initialSettings,
                        state = state,
                        remoteManifest = manifest,
                        json = json,
                    ),
                )
                var currentState = bootstrapDirtyObjects.fold(state) { acc, objectKey ->
                    acc.markDirty(objectKey)
                }
                val pullResult = pullRemoteObjects(config, manifest, currentState)
                currentState = pullResult.state
                val pushResult = pushDirtyObjects(config, manifest, currentState)
                manifest = pushResult.manifest
                currentState = pushResult.state

                val manifestBytes = json.encodeToString(manifest).encodeToByteArray()
                objectStore.write(config, MANIFEST_PATH, manifestBytes, JSON_CONTENT_TYPE)
                val finishedAt = nowMillis()
                val settingsAfterPull = settingsStore.settingsFlow.value
                val selectedAssistantHasConversations = conversationRepository.countConversationsOfAssistant(
                    settingsAfterPull.assistantId
                ) > 0
                val visibleConversationAssistantIds = if (reason == SyncReason.Manual) {
                    conversationRepository.getAssistantIdsWithConversations()
                } else {
                    pullResult.pulledConversationAssistantIds.toList()
                }
                val nextState = currentState.copy(
                    lastPullAt = finishedAt,
                    lastPushAt = if (pushResult.pushed) finishedAt else currentState.lastPushAt,
                    lastManifestHash = sha256Hash(manifestBytes),
                    lastRemoteObjectCount = manifest.objects.size,
                    lastPulledConversationCount = pullResult.pulledConversationCount,
                    lastPulledSettingsCount = pullResult.pulledSettingsCount,
                    lastPushedObjectCount = pushResult.pushedObjectCount,
                    lastError = null,
                )
                val remoteAssistantSettingsPresent = settingsObjectKey(SettingsSyncKind.Assistants) in manifest.objects
                suppressSettingsDirty = true
                try {
                    settingsStore.update {
                        it.copy(cloudSyncState = nextState)
                            .withConversationAssistantsVisible(
                                conversationAssistantIds = visibleConversationAssistantIds,
                                selectedAssistantHasConversations = selectedAssistantHasConversations,
                                allowPlaceholderAssistants = !remoteAssistantSettingsPresent,
                            )
                    }
                } finally {
                    suppressSettingsDirty = false
                }
                _status.value = CloudSyncStatus.Synced
            }.onFailure { error ->
                settingsStore.update {
                    it.copy(cloudSyncState = it.cloudSyncState.copy(lastError = error.message))
                }
                _status.value = CloudSyncStatus.Error
                throw error
            }
        }
    }

    private suspend fun pullRemoteObjects(
        config: CloudSyncConfig,
        manifest: SyncManifest,
        state: CloudSyncState,
    ): PullResult {
        var currentState = state
        val pulledConversationAssistantIds = mutableSetOf<Uuid>()
        var pulledConversationCount = 0
        var pulledSettingsCount = 0
        manifest.objects.forEach { (key, entry) ->
            when {
                key.startsWith("conversation:") -> {
                    if (!entry.shouldPullObject(currentState, key)) return@forEach
                    val result = pullConversation(config, key, entry, currentState)
                    currentState = result.state
                    result.assistantId?.let { pulledConversationAssistantIds.add(it) }
                    if (result.pulledConversation) {
                        pulledConversationCount++
                    }
                }

                key.startsWith("settings:") && entry.shouldPullSettingsObject(currentState, key) -> {
                    val result = pullSettings(config, key, entry, currentState)
                    currentState = result.state
                    if (result.pulledSettings) {
                        pulledSettingsCount++
                    }
                }
            }
        }
        return PullResult(
            state = currentState,
            pulledConversationAssistantIds = pulledConversationAssistantIds,
            pulledConversationCount = pulledConversationCount,
            pulledSettingsCount = pulledSettingsCount,
        )
    }

    private suspend fun pullConversation(
        config: CloudSyncConfig,
        key: String,
        entry: SyncManifestEntry,
        state: CloudSyncState,
    ): PullConversationResult {
        val conversationId = Uuid.parse(key.removePrefix("conversation:"))
        if (entry.deleted) {
            conversationRepository.deleteConversationFromSync(conversationId)
            return PullConversationResult(
                state = state.updateObjectState(key, entry).clearDirty(setOf(key)),
                assistantId = null,
                pulledConversation = false,
            )
        }

        val bytes = objectStore.read(config, entry.path) ?: return PullConversationResult(state, null, false)
        val envelope = json.decodeFromString<ConversationEnvelope>(bytes.decodeToString())
        val replacements = downloadReferencedFiles(config, envelope.files)
        val incoming = envelope.conversation.rewriteFileUris(replacements)
        val local = conversationRepository.getConversationById(conversationId)
        val merged = if (local == null) incoming else mergeConversations(local, incoming)
        conversationRepository.upsertConversationFromSync(merged)

        val updated = state.updateObjectState(key, entry)
        return PullConversationResult(
            state = if (key in state.dirtyObjects) updated.markDirty(key) else updated.clearDirty(setOf(key)),
            assistantId = merged.assistantId,
            pulledConversation = true,
        )
    }

    private suspend fun pullSettings(
        config: CloudSyncConfig,
        key: String,
        entry: SyncManifestEntry,
        state: CloudSyncState,
    ): PullSettingsResult {
        val bytes = objectStore.read(config, entry.path) ?: return PullSettingsResult(state, false)
        val envelope = json.decodeFromString<SettingsSyncEnvelope>(bytes.decodeToString())
        suppressSettingsDirty = true
        try {
            settingsStore.update { settings ->
                applySettingsSyncEnvelope(settings, envelope, json)
            }
        } finally {
            suppressSettingsDirty = false
        }
        return PullSettingsResult(
            state = state.updateObjectState(key, entry).clearDirty(setOf(key)),
            pulledSettings = true,
        )
    }

    private suspend fun pushDirtyObjects(
        config: CloudSyncConfig,
        manifest: SyncManifest,
        state: CloudSyncState,
    ): PushResult {
        var currentManifest = manifest
        var currentState = state
        val pushedKeys = mutableSetOf<String>()
        val deviceId = state.deviceId

        state.dirtyObjects.forEach { key ->
            when {
                key.startsWith("conversation:") -> {
                    val result = pushConversation(config, currentManifest, currentState, key, deviceId)
                    currentManifest = result.manifest
                    currentState = result.state
                    pushedKeys.add(key)
                }

                key.startsWith("settings:") -> {
                    val result = pushSettings(config, currentManifest, currentState, key, deviceId)
                    currentManifest = result.manifest
                    currentState = result.state
                    pushedKeys.add(key)
                }
            }
        }

        currentState = currentState.clearDirty(pushedKeys)
        return PushResult(
            manifest = currentManifest.copy(updatedAt = nowMillis()),
            state = currentState,
            pushed = pushedKeys.isNotEmpty(),
            pushedObjectCount = pushedKeys.size,
        )
    }

    private suspend fun pushConversation(
        config: CloudSyncConfig,
        manifest: SyncManifest,
        state: CloudSyncState,
        key: String,
        deviceId: String,
    ): PushResult {
        val conversationId = Uuid.parse(key.removePrefix("conversation:"))
        val previous = state.objectStates[key]
        val deleted = previous?.deleted == true && conversationRepository.getConversationById(conversationId) == null
        val version = nextSyncVersion(previous, manifest.objects[key])
        val updatedAt = nowMillis()

        if (deleted) {
            val tombstone = SyncTombstone(key = key, deletedAt = updatedAt, deviceId = deviceId)
            val bytes = json.encodeToString(tombstone).encodeToByteArray()
            val entry = SyncManifestEntry(
                path = deletedConversationPath(conversationId.toString()),
                version = version,
                updatedAt = updatedAt,
                hash = sha256Hash(bytes),
                deviceId = deviceId,
                deleted = true,
            )
            objectStore.write(config, entry.path, bytes, JSON_CONTENT_TYPE)
            return PushResult(
                manifest = manifest.withObject(key, entry),
                state = state.updateObjectState(key, entry),
                pushed = true,
                pushedObjectCount = 1,
            )
        }

        val conversation = conversationRepository.getConversationById(conversationId)
            ?: return pushDeletedConversation(config, manifest, state, key, conversationId, deviceId, version, updatedAt)
        val filesResult = uploadReferencedFiles(config, manifest, conversation, deviceId, updatedAt)
        val envelope = buildConversationEnvelope(
            conversation = conversation,
            version = version,
            updatedAt = updatedAt,
            deviceId = deviceId,
            files = filesResult.files,
        )
        val bytes = json.encodeToString(envelope).encodeToByteArray()
        val entry = SyncManifestEntry(
            path = conversationPath(conversation.id.toString()),
            version = version,
            updatedAt = updatedAt,
            hash = sha256Hash(bytes),
            deviceId = deviceId,
        )
        objectStore.write(config, entry.path, bytes, JSON_CONTENT_TYPE)
        return PushResult(
            manifest = filesResult.manifest.withObject(key, entry),
            state = state.updateObjectState(key, entry),
            pushed = true,
            pushedObjectCount = 1,
        )
    }

    private suspend fun pushDeletedConversation(
        config: CloudSyncConfig,
        manifest: SyncManifest,
        state: CloudSyncState,
        key: String,
        conversationId: Uuid,
        deviceId: String,
        version: Long,
        updatedAt: Long,
    ): PushResult {
        val tombstone = SyncTombstone(key = key, deletedAt = updatedAt, deviceId = deviceId)
        val bytes = json.encodeToString(tombstone).encodeToByteArray()
        val entry = SyncManifestEntry(
            path = deletedConversationPath(conversationId.toString()),
            version = version,
            updatedAt = updatedAt,
            hash = sha256Hash(bytes),
            deviceId = deviceId,
            deleted = true,
        )
        objectStore.write(config, entry.path, bytes, JSON_CONTENT_TYPE)
        return PushResult(
            manifest = manifest.withObject(key, entry),
            state = state.updateObjectState(key, entry),
            pushed = true,
            pushedObjectCount = 1,
        )
    }

    private suspend fun pushSettings(
        config: CloudSyncConfig,
        manifest: SyncManifest,
        state: CloudSyncState,
        key: String,
        deviceId: String,
    ): PushResult {
        val kind = SettingsSyncKind.entries.firstOrNull { settingsObjectKey(it) == key } ?: return PushResult(
            manifest = manifest,
            state = state,
            pushed = false,
            pushedObjectCount = 0,
        )
        val previous = state.objectStates[key]
        val version = nextSyncVersion(previous, manifest.objects[key])
        val updatedAt = nowMillis()
        val envelope = buildSettingsSyncEnvelope(
            kind = kind,
            settings = settingsStore.settingsFlow.value,
            updatedAt = updatedAt,
            deviceId = deviceId,
            json = json,
        )
        val bytes = json.encodeToString(envelope).encodeToByteArray()
        val entry = SyncManifestEntry(
            path = kind.path,
            version = version,
            updatedAt = updatedAt,
            hash = sha256Hash(bytes),
            deviceId = deviceId,
        )
        objectStore.write(config, entry.path, bytes, JSON_CONTENT_TYPE)
        return PushResult(
            manifest = manifest.withObject(key, entry),
            state = state.updateObjectState(key, entry),
            pushed = true,
            pushedObjectCount = 1,
        )
    }

    private suspend fun uploadReferencedFiles(
        config: CloudSyncConfig,
        manifest: SyncManifest,
        conversation: Conversation,
        deviceId: String,
        updatedAt: Long,
    ): FileUploadResult {
        var currentManifest = manifest
        val files = conversation.files.distinct().mapNotNull { uri ->
            val file = uri.toLocalFileOrNull()?.takeIf { it.exists() && it.isFile } ?: return@mapNotNull null
            val hash = sha256Hash(file)
            val path = remoteFilePath(hash)
            val key = fileObjectKey(hash)
            if (currentManifest.objects[key]?.hash != hash) {
                objectStore.writeFile(config, path, file, "application/octet-stream")
                val previous = currentManifest.objects[key]
                val entry = SyncManifestEntry(
                    path = path,
                    version = (previous?.version ?: 0L) + 1L,
                    updatedAt = updatedAt,
                    hash = hash,
                    deviceId = deviceId,
                )
                currentManifest = currentManifest.withObject(key, entry)
            }
            ReferencedFile(
                uri = uri.toString(),
                hash = hash,
                path = path,
                size = file.length(),
                displayName = file.name,
            )
        }
        return FileUploadResult(files = files, manifest = currentManifest)
    }

    private suspend fun downloadReferencedFiles(
        config: CloudSyncConfig,
        files: List<ReferencedFile>,
    ): Map<String, String> {
        return files.mapNotNull { file ->
            val existing = file.uri.toUri().toLocalFileOrNull()
            if (existing != null && existing.exists()) {
                return@mapNotNull file.uri to existing.toUri().toString()
            }
            val bytes = objectStore.read(config, file.path) ?: return@mapNotNull null
            val entity = filesManager.saveUploadFromBytes(
                bytes = bytes,
                displayName = file.displayName ?: file.path.substringAfterLast('/'),
                mimeType = file.mime ?: "application/octet-stream",
            )
            file.uri to filesManager.getFile(entity).toUri().toString()
        }.toMap()
    }

    private suspend fun readManifest(config: CloudSyncConfig): SyncManifest {
        val bytes = objectStore.read(config, MANIFEST_PATH) ?: return SyncManifest()
        return json.decodeFromString<SyncManifest>(bytes.decodeToString())
    }

    private suspend fun markDirty(objectKey: String) {
        updateCloudState { it.markDirty(objectKey) }
    }

    private suspend fun updateCloudState(transform: (CloudSyncState) -> CloudSyncState) {
        settingsStore.update { settings ->
            val state = ensureDeviceState(settings.cloudSyncState)
            settings.copy(cloudSyncState = transform(state))
        }
    }

    private fun ensureDeviceState(state: CloudSyncState): CloudSyncState {
        return if (state.deviceId.isNotBlank()) state else state.copy(deviceId = Uuid.random().toString())
    }

    private fun SyncManifest.withObject(
        key: String,
        entry: SyncManifestEntry,
    ): SyncManifest {
        return copy(
            updatedAt = maxOf(updatedAt, entry.updatedAt),
            objects = objects + (key to entry),
        )
    }

    private fun Conversation.rewriteFileUris(replacements: Map<String, String>): Conversation {
        if (replacements.isEmpty()) return this
        return copy(
            messageNodes = messageNodes.map { node ->
                node.copy(
                    messages = node.messages.map { message ->
                        message.copy(parts = message.parts.map { it.rewriteFileUris(replacements) })
                    }
                )
            }
        )
    }

    private fun UIMessagePart.rewriteFileUris(replacements: Map<String, String>): UIMessagePart {
        return when (this) {
            is UIMessagePart.Image -> copy(url = replacements[url] ?: url)
            is UIMessagePart.Document -> copy(url = replacements[url] ?: url)
            is UIMessagePart.Video -> copy(url = replacements[url] ?: url)
            is UIMessagePart.Audio -> copy(url = replacements[url] ?: url)
            is UIMessagePart.Tool -> copy(output = output.map { it.rewriteFileUris(replacements) })
            else -> this
        }
    }

    private fun Uri.toLocalFileOrNull(): File? {
        return runCatching {
            if (scheme == "file") toFile() else null
        }.getOrNull()
    }

    private fun nowMillis(): Long = Instant.now().toEpochMilli()

    private fun AppScope.launchSync(reason: SyncReason) {
        launch {
            runCatching { syncNow(reason) }
        }
    }

    private fun AppScope.launchSyncAfterSettingsReady() {
        launch {
            settingsStore.settingsFlowRaw.first()
            runCatching { syncNow(SyncReason.AppStart) }
        }
    }

    private fun conversationPath(conversationId: String): String = "conversations/$conversationId.json"

    private fun deletedConversationPath(conversationId: String): String = "conversations/$conversationId.deleted.json"

    private data class PushResult(
        val manifest: SyncManifest,
        val state: CloudSyncState,
        val pushed: Boolean,
        val pushedObjectCount: Int,
    )

    private data class PullResult(
        val state: CloudSyncState,
        val pulledConversationAssistantIds: Set<Uuid>,
        val pulledConversationCount: Int,
        val pulledSettingsCount: Int,
    )

    private data class PullConversationResult(
        val state: CloudSyncState,
        val assistantId: Uuid?,
        val pulledConversation: Boolean,
    )

    private data class PullSettingsResult(
        val state: CloudSyncState,
        val pulledSettings: Boolean,
    )

    private data class FileUploadResult(
        val files: List<ReferencedFile>,
        val manifest: SyncManifest,
    )
}

@Serializable
private data class SyncTombstone(
    val key: String,
    val deletedAt: Long,
    val deviceId: String,
)
