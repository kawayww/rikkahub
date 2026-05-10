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
): Boolean {
    if (deleted) return false
    if (key in state.dirtyObjects) return false
    return shouldPullObject(state, key)
}

internal fun nextSyncVersion(
    local: LocalObjectState?,
    remote: SyncManifestEntry?,
): Long {
    return maxOf(local?.version ?: 0L, remote?.version ?: 0L) + 1L
}
