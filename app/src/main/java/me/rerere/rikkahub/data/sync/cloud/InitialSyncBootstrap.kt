package me.rerere.rikkahub.data.sync.cloud

fun initialSyncDirtyObjects(
    remoteManifest: SyncManifest,
    state: CloudSyncState,
    localConversationIds: List<String>,
    settingsKinds: Set<SettingsSyncKind> = SettingsSyncKind.entries.toSet(),
): Set<String> {
    val remoteObjects = remoteManifest.objects

    return buildSet {
        localConversationIds.forEach { conversationId ->
            val key = conversationObjectKey(conversationId)
            if (key !in remoteObjects) {
                add(key)
            }
        }
        settingsKinds.forEach { kind ->
            val key = settingsObjectKey(kind)
            if (key !in remoteObjects) {
                add(key)
            }
        }
    }
}
