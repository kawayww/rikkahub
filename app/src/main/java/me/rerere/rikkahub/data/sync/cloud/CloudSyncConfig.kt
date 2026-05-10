package me.rerere.rikkahub.data.sync.cloud

import kotlinx.serialization.Serializable

@Serializable
data class CloudSyncConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_sync",
)

enum class CloudSyncStatus {
    Idle,
    Syncing,
    Synced,
    WaitingForNetwork,
    Error,
}

enum class SyncReason {
    AppStart,
    Foreground,
    Manual,
    LocalChange,
}
