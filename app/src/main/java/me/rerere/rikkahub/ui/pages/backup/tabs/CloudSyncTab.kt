package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.sync.cloud.CloudSyncConfig
import me.rerere.rikkahub.data.sync.cloud.CloudSyncStatus
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.backup.CloudSyncUiEvent
import me.rerere.rikkahub.utils.toLocalDateTime
import java.time.Instant

@Composable
fun CloudSyncTab(vm: BackupVM) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.cloudSyncStatus.collectAsStateWithLifecycle()
    val isTesting by vm.isTestingCloudSync.collectAsStateWithLifecycle()
    val isSyncing by vm.isManualCloudSyncing.collectAsStateWithLifecycle()
    val config = settings.cloudSyncConfig
    val state = settings.cloudSyncState
    val toaster = LocalToaster.current
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.cloudSyncEvents.collect { event ->
            when (event) {
                CloudSyncUiEvent.ConnectionSuccess -> {
                    toaster.show(
                        message = context.getString(R.string.backup_page_connection_success),
                        type = ToastType.Success,
                    )
                }

                is CloudSyncUiEvent.ConnectionFailed -> {
                    toaster.show(
                        message = context.getString(R.string.backup_page_connection_failed, event.message),
                        type = ToastType.Error,
                    )
                }

                CloudSyncUiEvent.SyncFinished -> {
                    toaster.show(
                        message = context.getString(R.string.backup_page_cloud_sync_finished),
                        type = ToastType.Success,
                    )
                }

                is CloudSyncUiEvent.SyncFailed -> {
                    toaster.show(
                        message = context.getString(R.string.backup_page_cloud_sync_failed, event.message),
                        type = ToastType.Error,
                    )
                }
            }
        }
    }

    fun updateConfig(newConfig: CloudSyncConfig) {
        vm.updateSettings(settings.copy(cloudSyncConfig = newConfig))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup {
                item(
                    trailingContent = {
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { updateConfig(config.copy(enabled = it)) },
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.backup_page_cloud_sync),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = status.label(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.backup_page_cloud_sync_dirty_objects,
                                    state.dirtyObjects.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.backup_page_cloud_sync_last_pull,
                                    state.lastPullAt.syncTimeText(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.backup_page_cloud_sync_last_push,
                                    state.lastPushAt.syncTimeText(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.backup_page_cloud_sync_last_result,
                                    state.lastRemoteObjectCount,
                                    state.lastPulledConversationCount,
                                    state.lastPulledSettingsCount,
                                    state.lastPushedObjectCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                )
            }

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_webdav_server_address)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = config.url,
                            onValueChange = { updateConfig(config.copy(url = it.trim())) },
                            placeholder = { Text("https://example.com/dav") },
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_username)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = config.username,
                            onValueChange = { updateConfig(config.copy(username = it.trim())) },
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_password)) },
                    supportingContent = {
                        var passwordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = config.password,
                            onValueChange = { updateConfig(config.copy(password = it)) },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = null,
                                    )
                                }
                            },
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_cloud_sync_sync_path)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = config.path,
                            onValueChange = { updateConfig(config.copy(path = it.trim())) },
                            singleLine = true,
                        )
                    },
                )
            }
        }

        HorizontalDivider()
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(
                enabled = !isTesting && !isSyncing,
                onClick = vm::testCloudSyncAsync,
            ) {
                Text(
                    if (isTesting) {
                        stringResource(R.string.backup_page_cloud_sync_testing)
                    } else {
                        stringResource(R.string.backup_page_test_connection)
                    }
                )
            }
            Button(
                enabled = config.enabled && !isSyncing && status != CloudSyncStatus.Syncing,
                onClick = vm::syncNowAsync,
            ) {
                if (isSyncing || status == CloudSyncStatus.Syncing) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(HugeIcons.Upload02, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isSyncing || status == CloudSyncStatus.Syncing) {
                        stringResource(R.string.backup_page_cloud_sync_syncing)
                    } else {
                        stringResource(R.string.backup_page_cloud_sync_sync_now)
                    }
                )
            }
        }
    }
}

@Composable
private fun CloudSyncStatus.label(): String {
    return when (this) {
        CloudSyncStatus.Idle -> stringResource(R.string.backup_page_cloud_sync_status_idle)
        CloudSyncStatus.Syncing -> stringResource(R.string.backup_page_cloud_sync_status_syncing)
        CloudSyncStatus.Synced -> stringResource(R.string.backup_page_cloud_sync_status_synced)
        CloudSyncStatus.WaitingForNetwork -> stringResource(R.string.backup_page_cloud_sync_status_waiting_for_network)
        CloudSyncStatus.Error -> stringResource(R.string.backup_page_cloud_sync_status_error)
    }
}

@Composable
private fun Long.syncTimeText(): String {
    return if (this <= 0L) {
        stringResource(R.string.backup_page_cloud_sync_never)
    } else {
        Instant.ofEpochMilli(this).toLocalDateTime()
    }
}
