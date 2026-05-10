package me.rerere.rikkahub.data.sync.cloud

fun initialSyncDirtyObjects(
    remoteManifest: SyncManifest,
    state: CloudSyncState,
    localConversationIds: List<String>,
    settingsKinds: Set<SettingsSyncKind> = SettingsSyncKind.entries.toSet(),
): Set<String> {
    if (remoteManifest.objects.isNotEmpty()) return emptySet()
    if (state.objectStates.isNotEmpty()) return emptySet()

    return localConversationIds.mapTo(mutableSetOf()) { conversationObjectKey(it) }
        .also { keys ->
            settingsKinds.forEach { kind ->
                keys.add(settingsObjectKey(kind))
            }
        }
}
