package me.rerere.rikkahub.data.sync.cloud

internal fun SyncManifestEntry.shouldPullObject(
    state: CloudSyncState,
    key: String,
): Boolean {
    val local = state.objectStates[key] ?: return true
    return version > local.version || (version == local.version && hash != local.hash)
}

internal fun SyncManifestEntry.shouldPullSettingsObject(
    state: CloudSyncState,
    key: String,
    reason: SyncReason,
): Boolean {
    if (deleted) return false
    if (shouldPullObject(state, key)) return true

    return reason == SyncReason.Manual && key in state.dirtyObjects
}
