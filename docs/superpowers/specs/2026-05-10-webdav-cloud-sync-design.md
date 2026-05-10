# WebDAV 云同步设计

## 背景

当前 WebDAV 功能是备份功能：应用把 `settings.json`、数据库文件和可选文件目录打成一个 zip，再上传到 WebDAV。恢复时下载 zip 并覆盖本地数据。

这个模式适合迁移和灾难恢复，但不适合“平板上聊天，手机打开继续”的日常跨设备体验。原因是：

1. 每次同步都围绕整包 zip，不是围绕用户刚改动的会话。
2. 恢复动作是手动的，而且语义是覆盖本地状态。
3. 备份包包含数据库和文件快照，不能自然表达单个会话、单个 assistant 或单个附件的更新。
4. 设备之间没有稳定的对象版本和删除标记，离线设备重新上线时容易把旧数据重新带回来。

本次设计新增一套 Cloud Sync。它复用 WebDAV 作为远端对象存储，但不复用现有 zip 备份协议。现有 WebDAV Backup 继续保留，Cloud Sync 独立负责自动增量同步。

## 目标

1. 用户在一台设备发送消息、生成回复、修改标题、置顶或删除会话后，另一台设备打开 app 或回到前台时能自动看到变化。
2. 同步粒度是对象级，不上传和下载整包数据库。
3. 第一版同步聊天继续所需的数据：会话、消息树、assistant 行为配置、prompt injection、lorebook、quick messages、必要模型选择关系、会话实际引用到的附件。
4. 用户层不出现手动版本选择。实现层要自动合并消息树，并用现有分支模型承接少见的并发写入。
5. 保留现有 WebDAV zip 备份功能，不让同步协议影响备份和恢复。
6. 不把 provider API key、WebDAV 密码、S3 密钥等敏感信息默认写入远端同步对象。

## 非目标

1. 不把整个 Room 数据库作为同步对象上传。
2. 不把整个 `upload` 目录默认同步。
3. 不同步 cache、日志、临时文件、历史备份 zip。
4. 不把 `skills` 目录纳入第一版自动同步。
5. 不做实时协作编辑，也不保证两台设备同时在线时达到毫秒级同步。
6. 不在第一版实现端到端加密密钥同步。
7. 不要求用户手动处理“保留哪个版本”的弹窗。

## 当前数据结构

### 会话

当前会话持久化分为两层：

1. `ConversationEntity` 存会话壳：`id`、`assistantId`、`title`、`createAt`、`updateAt`、`chatSuggestions`、`isPinned`。
2. `MessageNodeEntity` 存消息树节点：`id`、`conversationId`、`nodeIndex`、`messages`、`selectIndex`。

`MessageNodeEntity.messages` 是 `List<UIMessage>` 的 JSON。`MessageNode` 里已经支持多个候选消息和 `selectIndex`，可以承接重新生成和分支。

### 保存路径

`ChatService.saveConversation()` 是会话写入主入口。当前保存时会：

1. 更新内存中的 `ConversationSession`。
2. 新会话走 `insertConversation()`。
3. 已存在会话走 `updateConversation()`。
4. `ConversationRepository.updateConversation()` 会更新 `ConversationEntity`，删除旧节点，再重新插入当前所有 `MessageNodeEntity`。

Cloud Sync 第一版应在保存成功后标记该会话为 dirty，而不是直接介入生成过程。

### 附件

消息中的本地附件通过 `UIMessagePart.Image`、`Document`、`Video`、`Audio` 的 `file://` URL 引用。`Conversation.files` 可以收集当前会话实际引用到的本地文件。

这意味着只同步会话 JSON 不够。另一台设备没有对应本地文件时，消息里的图片或文档会断开。因此第一版需要同步“会话实际引用到的附件”，但不要同步整个 `upload` 文件夹。

### 设置

`Settings` 存在 `PreferencesStore` 中，包含设备偏好、provider、assistant、prompt injection、lorebook、WebDAV 配置、S3 配置、显示设置等混合信息。

Cloud Sync 不能整包同步 `Settings`，否则会把设备级设置和敏感密钥一起写到远端。第一版必须把 settings 拆成明确的同步对象。
Cloud Sync 自己使用单独的同步配置对象，不复用现有 WebDAV 备份配置。

## 产品行为

### 启用入口

新增 Cloud Sync 设置入口，和现有备份功能分开。

建议数据页结构：

```text
Data
  Cloud Sync
  WebDAV Backup
  S3 Backup
  Import/Export
  Reminder
```

Cloud Sync 页面包含：

```text
启用自动同步
同步服务：WebDAV
服务器地址
用户名
密码
路径
立即同步
最后同步时间
同步状态
同步范围说明
```

同步路径默认使用：

```text
rikkahub_sync
```

现有备份路径继续使用当前 `rikkahub_backups` 语义，不和 Cloud Sync 混用。

### 自动同步时机

Cloud Sync 在这些时机触发拉取：

1. app 启动后。
2. app 从后台回到前台后。
3. 用户点击“立即同步”。
4. 后台推送完成前可做一次轻量远端检查，避免覆盖远端新对象。

Cloud Sync 在这些时机触发推送：

1. 用户消息保存成功后。
2. assistant 最终回复保存成功后。
3. 会话标题更新后。
4. 会话置顶状态更新后。
5. 消息删除、分支选择、重新生成保存后。
6. assistant、prompt injection、lorebook、quick message 等同步范围内设置更新后。

流式生成过程中不按 token 同步。只在稳定保存点同步，避免产生大量小对象写入。

### 用户可见状态

同步状态建议分为：

```kotlin
Idle
Syncing
Synced
WaitingForNetwork
Error
```

页面展示：

1. 最后成功同步时间。
2. 当前同步状态。
3. 最近一次错误的简短原因。
4. “立即同步”按钮。

普通聊天页面不弹出同步错误，除非错误导致用户明确操作失败。同步失败时保留本地 dirty 状态，下一次自动重试。

## 远端布局

远端 WebDAV 目录结构：

```text
<sync-path>/
  manifest.json
  devices/
    <device-id>.json
  conversations/
    <conversation-id>.json
    <conversation-id>.deleted.json
  settings/
    assistants.json
    assistant-tags.json
    prompt-injections.json
    lorebooks.json
    quick-messages.json
    model-selection.json
  files/
    <content-hash>
```

`manifest.json` 是索引，不是数据库。它记录每个对象的路径、版本、更新时间、hash、删除标记和来源设备。

示例：

```json
{
  "schemaVersion": 1,
  "updatedAt": 1778390000000,
  "objects": {
    "conversation:6f5e": {
      "path": "conversations/6f5e.json",
      "version": 12,
      "updatedAt": 1778390000000,
      "hash": "sha256:...",
      "deviceId": "tablet-a",
      "deleted": false
    },
    "settings:assistants": {
      "path": "settings/assistants.json",
      "version": 4,
      "updatedAt": 1778389900000,
      "hash": "sha256:...",
      "deviceId": "tablet-a",
      "deleted": false
    },
    "file:sha256:abc": {
      "path": "files/abc",
      "version": 1,
      "updatedAt": 1778389800000,
      "hash": "sha256:abc",
      "deviceId": "tablet-a",
      "deleted": false
    }
  }
}
```

## 同步对象

### Conversation 对象

`conversations/<conversation-id>.json` 包含完整 `Conversation` 结构，但使用同步专用 envelope 包装。

```json
{
  "schemaVersion": 1,
  "kind": "conversation",
  "id": "6f5e",
  "version": 12,
  "updatedAt": 1778390000000,
  "deviceId": "tablet-a",
  "payload": {
    "id": "6f5e",
    "assistantId": "...",
    "title": "...",
    "messageNodes": [],
    "chatSuggestions": [],
    "isPinned": false,
    "createAt": "...",
    "updateAt": "..."
  },
  "referencedFiles": [
    {
      "hash": "sha256:abc",
      "path": "files/abc",
      "localName": "upload/uuid.png",
      "mime": "image/png",
      "size": 12345
    }
  ]
}
```

要求：

1. `payload` 复用现有 `Conversation` 序列化，减少模型转换风险。
2. `referencedFiles` 只列出这个会话实际引用到的文件。
3. 本地导入远端会话后，如果附件缺失，先保留消息，再后台拉取文件。

### Settings 对象

第一版拆分同步这些对象：

```text
settings/assistants.json
settings/assistant-tags.json
settings/prompt-injections.json
settings/lorebooks.json
settings/quick-messages.json
settings/model-selection.json
```

`assistants.json` 同步 `Settings.assistants`。

`assistant-tags.json` 同步 `Settings.assistantTags`。

`prompt-injections.json` 同步 `Settings.modeInjections`。

`lorebooks.json` 同步 `Settings.lorebooks`。

`quick-messages.json` 同步 `Settings.quickMessages`。

`model-selection.json` 同步非敏感模型选择关系：

```json
{
  "chatModelId": "...",
  "titleModelId": "...",
  "translateModeId": "...",
  "suggestionModelId": "...",
  "imageGenerationModelId": "...",
  "ocrModelId": "...",
  "compressModelId": "..."
}
```

第一版不自动同步：

```text
assistantId
displaySetting
dynamicColor
themeId
developerMode
providers
ttsProviders
searchServices
mcpServers
backup WebDAV config
backup S3 config
webServerAccessPassword
webServerJwtEnabled
webServerPort
webServerLocalhostOnly
backupReminderConfig
launchCount
sponsorAlertDismissedAt
custom provider headers
custom provider bodies
```

如果远端会话引用的 assistant 或 model 在本机缺失：

1. assistant 缺失时，拉取 settings 对象后应恢复。
2. model/provider 缺失时，保留会话和 assistant 配置，但聊天时提示用户本机需要配置对应 provider。

### File 对象

文件对象以内容 hash 命名：

```text
files/<sha256>
```

同步流程：

1. 上传会话前扫描 `Conversation.files`。
2. 对每个本地文件计算 SHA-256。
3. 远端不存在该 hash 时上传。
4. conversation envelope 记录原始本地相对路径、mime、size、hash。
5. 另一台设备下载后保存到本地 `upload` 目录，并更新消息中的 `file://` URL 指向新设备本地路径。

这里不能直接复用原设备的绝对 `file://` 路径。导入远端会话时必须做路径重写。

## 本地状态

新增本地同步状态表或 DataStore 对象，用于记录：

```kotlin
data class CloudSyncState(
    val deviceId: String,
    val enabled: Boolean,
    val lastPullAt: Long,
    val lastPushAt: Long,
    val lastManifestHash: String?,
    val objectStates: Map<String, LocalObjectState>,
    val dirtyObjects: Set<String>,
)

data class LocalObjectState(
    val version: Long,
    val updatedAt: Long,
    val hash: String,
    val deleted: Boolean,
)
```

`deviceId` 首次启用同步时生成并持久化，不随 app 重启变化。

dirty 对象在本地保存成功后写入。推送成功并确认 manifest 更新后才清除。

## 同步流程

### 首次启用

1. 用户填写 WebDAV 同步配置并测试连接。
2. app 创建远端 sync 根目录。
3. 如果远端没有 `manifest.json`，创建空 manifest。
4. 本地生成 `deviceId`。
5. 写入 `devices/<device-id>.json`。
6. 做一次 full scan，把本地会话和同步范围内 settings 标记为 dirty。
7. 执行一次推送。

### 启动拉取

1. 读取本地 `CloudSyncState`。
2. 下载远端 `manifest.json`。
3. 和本地 object state 对比。
4. 找出远端更新或删除的对象。
5. 下载变化的 conversation/settings/file。
6. 写入 Room、DataStore 和本地文件目录。
7. 对更新过的会话重建 FTS 索引。
8. 更新本地 object state。

拉取失败不阻塞 app 启动。失败只更新同步状态，保留本地数据。

### 本地推送

1. 收集 dirty objects。
2. 先拉远端 manifest。
3. 对每个 dirty object 构建 envelope。
4. 上传对象文件。
5. 上传缺失的 referenced files。
6. 合并 manifest 条目。
7. 上传新的 `manifest.json`。
8. 更新本地 object state。
9. 清除已成功推送的 dirty objects。

推送时要 debounce，避免连续保存产生大量请求。建议初始 debounce 为 2 秒。

### 删除同步

删除会话时不直接依赖远端文件缺失表达删除。需要写 tombstone：

```text
conversations/<conversation-id>.deleted.json
```

manifest 中对应对象设置：

```json
{
  "deleted": true,
  "path": "conversations/<conversation-id>.deleted.json"
}
```

另一台设备拉到删除标记后：

1. 删除本地会话。
2. 删除 FTS 索引。
3. 对本地附件做引用检查。
4. 没有任何会话引用的附件可以延迟清理。

删除标记至少保留 30 天，避免长时间离线设备重新上线后复活旧会话。

## 自动合并规则

用户层不展示版本选择。实现层按对象类型自动处理。

### 会话壳字段

这些字段使用版本较新的值：

```text
title
isPinned
assistantId
chatSuggestions
updateAt
```

`createAt` 保留最早值。

### 消息节点

消息节点按 `MessageNode.id` 合并：

1. 两边都有同一个 node 时，按 `UIMessage.id` 合并 `messages` 列表。
2. 只存在一边的 node 直接保留。
3. `nodeIndex` 以较新会话的顺序为主，缺失节点按原相对顺序补入。
4. `selectIndex` 使用较新会话的选择；如果越界，裁剪到最后一个可用候选。

如果两台设备离线期间对同一个节点产生不同 assistant 回复，这些回复会作为同一个 `MessageNode.messages` 的多个候选保留。现有分支 UI 已经可以承接这种情况。

### 消息内容

同一个 `UIMessage.id` 的消息使用较新对象中的内容。

不同 `UIMessage.id` 不互相覆盖，保留为节点候选或新节点。

### Settings 对象

settings 对象第一版使用对象级版本较新者为准。

对于列表型对象，可在后续版本按 id 做更细合并。第一版为了降低实现风险，先保证明确、稳定、可测试的对象级同步。

## WebDAV 写入安全

WebDAV 没有统一可靠的事务能力。第一版采用“先对象，后 manifest”的顺序：

1. 上传 conversation/settings/file 对象。
2. 上传 manifest 临时文件 `manifest.json.tmp.<device-id>`。
3. 上传最终 `manifest.json`。
4. 拉取时如果看到对象存在但 manifest 未引用，忽略它。

如果两个设备几乎同时上传 manifest，后写入者可能覆盖前写入者。为减少丢失：

1. 每次推送前必须先拉最新 manifest。
2. 上传 manifest 前再次拉取并合并远端新增条目。
3. manifest 合并以 object key 为单位，不整包替换本地视图。
4. 对 manifest 中同一 object key，保留版本更高或 `updatedAt` 更新的条目。

这不能让 WebDAV 变成强事务数据库，但足以支持个人多设备的近实时同步体验。

## 代码结构

新增同步模块：

```text
app/src/main/java/me/rerere/rikkahub/data/sync/cloud/
  CloudSyncConfig.kt
  CloudSyncManager.kt
  CloudSyncState.kt
  SyncManifest.kt
  SyncObjectEnvelope.kt
  ConversationSyncCodec.kt
  SettingsSyncCodec.kt
  ReferencedFileSync.kt
  SyncScheduler.kt
```

新增 WebDAV 对象存储适配：

```text
app/src/main/java/me/rerere/rikkahub/data/sync/webdav/
  WebDavObjectStore.kt
```

保留现有：

```text
app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavSync.kt
```

`WebDavSync.kt` 继续只负责 zip 备份，不加入 Cloud Sync 的业务逻辑。

### 关键接口

```kotlin
interface SyncObjectStore {
    suspend fun ensureRoot()
    suspend fun readBytes(path: String): ByteArray?
    suspend fun writeBytes(path: String, bytes: ByteArray, contentType: String)
    suspend fun exists(path: String): Boolean
    suspend fun list(path: String): List<RemoteObjectInfo>
}
```

`WebDavObjectStore` 基于现有 `WebDavClient` 实现这个接口。

`CloudSyncManager` 对外提供：

```kotlin
suspend fun syncNow(reason: SyncReason)
fun markConversationDirty(id: Uuid)
fun markConversationDeleted(id: Uuid)
fun markSettingsDirty(kind: SettingsSyncKind)
fun observeStatus(): StateFlow<CloudSyncStatus>
```

## 接入点

### 会话保存

`ChatService.saveConversation()` 在 repository 保存成功后调用：

```kotlin
cloudSyncManager.markConversationDirty(conversation.id)
```

调用必须放在本地保存成功之后，避免远端同步到本地未落库的对象。

### 会话删除

`ConversationRepository.deleteConversation()` 或上层删除入口在本地删除成功后调用：

```kotlin
cloudSyncManager.markConversationDeleted(conversation.id)
```

### 设置更新

`SettingsStore.update()` 不能粗暴把整个 settings 标 dirty。需要比较更新前后字段，针对同步范围内字段标记对应 dirty kind：

```text
assistants
assistantTags
modeInjections
lorebooks
quickMessages
modelSelection
```

WebDAV 密码、provider、display setting 等字段变化不触发 Cloud Sync。

### 生命周期

复用 `ProcessLifecycleOwner`：

1. app 进入前台时触发一次 pull。
2. dirty objects 存在时调度 push。
3. 网络不可用时进入 `WaitingForNetwork`，下次前台或网络恢复后重试。

第一版可以不接入 WorkManager 周期任务；只做前台触发和保存后触发，降低后台限制带来的不确定性。

## 安全边界

第一版远端同步对象可能包含聊天内容、assistant prompt、lorebook 和附件。它们本身属于隐私数据。

因此 UI 必须明确说明：

1. Cloud Sync 会把聊天内容和相关附件上传到用户配置的 WebDAV。
2. Cloud Sync 不默认同步 API key、WebDAV 密码、S3 密钥。
3. 如果 WebDAV 服务不可信，用户不应启用同步。

后续可以增加加密模式，但第一版不把加密作为前置条件。

## 错误处理

1. WebDAV 未配置或连接失败：同步状态为 `Error`，不影响本地聊天。
2. manifest 下载失败：跳过本轮同步，保留 dirty 状态。
3. 单个 conversation 下载失败：记录错误，继续处理其他对象。
4. 附件下载失败：保留消息，附件位置显示为缺失状态，下一轮重试。
5. JSON schema 不兼容：忽略该对象并记录错误，不覆盖本地对象。
6. provider/model 缺失：保留会话和 assistant，聊天时提示本机需要配置对应模型。

## 迁移与兼容

1. 现有 WebDAV Backup 不迁移到 Cloud Sync。
2. 用户首次启用 Cloud Sync 时，从本地当前数据库生成同步对象。
3. 旧设备未启用 Cloud Sync 时不受影响。
4. Cloud Sync schema 使用 `schemaVersion`，后续对象格式变化通过 codec 做兼容。

## 测试

建议至少覆盖：

1. `SyncManifest` 序列化和反序列化。
2. manifest 合并规则：新增对象、更新对象、删除标记。
3. `ConversationSyncCodec` 能导出和导入完整消息树。
4. 同一 `MessageNode.id` 下不同 `UIMessage.id` 会保留为候选消息。
5. `selectIndex` 越界时能安全裁剪。
6. 删除会话会生成 tombstone。
7. 拉到 tombstone 后本地会话和 FTS 索引被删除。
8. 附件 hash 计算、上传、下载、路径重写。
9. settings 拆分对象不会包含 provider API key。
10. `SettingsStore.update()` 只对同步范围内字段标 dirty。
11. `ChatService.saveConversation()` 保存成功后标记会话 dirty。
12. WebDAV 对象存储能读写 manifest、conversation 和 file 对象。
13. 同步失败时 dirty 状态保留，下一轮可重试。

## 验收标准

第一版完成后应满足：

1. 平板发送一条纯文本消息，手机打开 app 后自动出现该会话。
2. 平板生成 assistant 回复完成后，手机打开 app 后能看到回复。
3. 平板发送带图片或文档的消息，手机同步后能看到消息并打开附件。
4. 手机修改标题或置顶，平板下一次前台同步后更新。
5. 删除会话能同步到另一台设备，且不会被离线旧数据复活。
6. 关闭网络时本地聊天不受影响，恢复网络或回到前台后自动补同步。
7. 远端同步对象不包含 provider API key、WebDAV 密码、S3 key。
8. 现有 WebDAV zip 备份、恢复、列表、删除功能不受影响。

## 结果形态

完成后，WebDAV 在应用里会有两种明确用途：

1. WebDAV Backup：手动整包备份和恢复。
2. Cloud Sync：自动对象级增量同步，用于多设备继续聊天。

用户日常使用时不需要下载备份包，也不需要手动恢复。打开另一台设备后，应用会自动拉取会话、行为配置和会话引用附件，让聊天上下文自然延续。
