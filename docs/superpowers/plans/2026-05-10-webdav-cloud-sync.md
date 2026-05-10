# WebDAV Cloud Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a first usable WebDAV-backed Cloud Sync path that syncs conversations, assistant-related settings, and referenced chat files without using zip backups.

**Architecture:** Add a new `data/sync/cloud` layer with pure manifest/state/codecs, a WebDAV object-store adapter, and a `CloudSyncManager` that coordinates pull/push. Existing `WebDavSync` remains zip-backup-only; `ChatService` and settings updates only mark dirty objects after local persistence succeeds.

**Tech Stack:** Kotlin, kotlinx.serialization, Room repositories, DataStore preferences, Koin, existing Ktor `HttpClient` WebDAV client, JUnit JVM tests for pure sync logic.

---

## File Map

- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/CloudSyncConfig.kt`
  - Serializable sync config, status enum, sync reason enum.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/CloudSyncState.kt`
  - Serializable local sync state and dirty object helpers.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/SyncManifest.kt`
  - Manifest model, object keys, merge rules.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/SyncObjectStore.kt`
  - Object-store boundary used by manager and tests.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/ConversationSyncCodec.kt`
  - Conversation envelope, referenced files, message-tree merge.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/SettingsSyncCodec.kt`
  - Export/import assistant settings slices without provider secrets.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/ReferencedFileSync.kt`
  - SHA-256 file hashing and local path mapping.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/CloudSyncManager.kt`
  - Pull/push orchestration, dirty marking, status flow.
- Create `app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavObjectStore.kt`
  - WebDAV-backed `SyncObjectStore`.
- Modify `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
  - Persist cloud sync config/state.
- Modify `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt`
  - Add full conversation listing and sync upsert/delete helpers.
- Modify `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
  - Mark conversations dirty after save/delete paths.
- Modify DI modules
  - Register `CloudSyncManager` and `WebDavObjectStore`.
- Modify backup UI
  - Add Cloud Sync tab and controls.
- Add tests under `app/src/test/java/me/rerere/rikkahub/data/sync/cloud/`
  - Pure sync rules, manifest merge, settings slicing, conversation merge.

## Task 1: Pure Sync Models

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/CloudSyncConfig.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/CloudSyncState.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/SyncManifest.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/sync/cloud/SyncManifestTest.kt`

- [ ] **Step 1: Write failing manifest tests**

Create tests for:

```kotlin
@Test
fun `merge keeps newer object entry`() {
    val local = SyncManifest(
        objects = mapOf(
            "conversation:a" to SyncManifestEntry(
                path = "conversations/a.json",
                version = 1,
                updatedAt = 10,
                hash = "old",
                deviceId = "phone"
            )
        )
    )
    val remote = SyncManifest(
        objects = mapOf(
            "conversation:a" to SyncManifestEntry(
                path = "conversations/a.json",
                version = 2,
                updatedAt = 20,
                hash = "new",
                deviceId = "tablet"
            )
        )
    )

    val merged = local.merge(remote)

    assertEquals("new", merged.objects.getValue("conversation:a").hash)
}

@Test
fun `dirty helpers add and clear object keys`() {
    val state = CloudSyncState(deviceId = "phone")
        .markDirty("conversation:a")
        .markDirty("settings:assistants")
        .clearDirty(setOf("conversation:a"))

    assertEquals(setOf("settings:assistants"), state.dirtyObjects)
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.sync.cloud.SyncManifestTest'
```

Expected: compile failure because sync classes do not exist.

- [ ] **Step 3: Implement models**

Implement serializable `CloudSyncConfig`, `CloudSyncState`, `LocalObjectState`, `SyncManifest`, and `SyncManifestEntry`.

- [ ] **Step 4: Run tests**

Run the same Gradle command. Expected: tests pass.

## Task 2: Conversation Sync Codec

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/ConversationSyncCodec.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/sync/cloud/ConversationSyncCodecTest.kt`

- [ ] **Step 1: Write failing codec tests**

Test that conversation envelopes serialize payloads and merge same-node different-message replies into alternatives.

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.sync.cloud.ConversationSyncCodecTest'
```

Expected: compile failure because codec does not exist.

- [ ] **Step 3: Implement codec**

Implement `ConversationEnvelope`, `ReferencedFile`, `conversationObjectKey()`, `mergeConversations()`, and safe `selectIndex` clipping.

- [ ] **Step 4: Run tests**

Run the same Gradle command. Expected: tests pass.

## Task 3: Settings Sync Codec

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/SettingsSyncCodec.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/sync/cloud/SettingsSyncCodecTest.kt`

- [ ] **Step 1: Write failing settings tests**

Test that settings slices include assistants, lorebooks, prompt injections, quick messages, and model selection, while excluding providers and backup credentials.

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.sync.cloud.SettingsSyncCodecTest'
```

Expected: compile failure because codec does not exist.

- [ ] **Step 3: Implement settings codec**

Implement `SettingsSyncKind`, `SettingsSyncPayload`, `exportSettingsPayload()`, `applySettingsPayload()`, and `dirtySettingsKinds(before, after)`.

- [ ] **Step 4: Run tests**

Run the same Gradle command. Expected: tests pass.

## Task 4: File Reference Sync

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/ReferencedFileSync.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/sync/cloud/ReferencedFileSyncTest.kt`

- [ ] **Step 1: Write failing file tests**

Test SHA-256 hashing and deterministic remote file path creation.

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.sync.cloud.ReferencedFileSyncTest'
```

Expected: compile failure because file sync helpers do not exist.

- [ ] **Step 3: Implement file helpers**

Implement streaming SHA-256 and `remoteFilePath(hash)`.

- [ ] **Step 4: Run tests**

Run the same Gradle command. Expected: tests pass.

## Task 5: Persistence Integration

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt`

- [ ] **Step 1: Add config/state to Settings**

Add `cloudSyncConfig` and `cloudSyncState` with defaults, read/write them through DataStore keys.

- [ ] **Step 2: Add repository methods**

Add methods for full conversation export, sync upsert, and sync delete without file cleanup surprises.

- [ ] **Step 3: Run compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds.

## Task 6: Object Store and Manager

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/SyncObjectStore.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/cloud/CloudSyncManager.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/sync/webdav/WebDavObjectStore.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`

- [ ] **Step 1: Implement object-store boundary**

Create `SyncObjectStore` and WebDAV adapter using existing `WebDavClient`.

- [ ] **Step 2: Implement manager**

Implement `syncNow()`, `markConversationDirty()`, `markConversationDeleted()`, and `markSettingsDirty()`.

- [ ] **Step 3: Register DI**

Register manager and object store in Koin.

- [ ] **Step 4: Run compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds.

## Task 7: Save/Delete Hooks

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`

- [ ] **Step 1: Hook conversation saves**

Inject `CloudSyncManager` into `ChatService` and mark conversation dirty only after repository insert/update succeeds.

- [ ] **Step 2: Hook setting changes**

In `SettingsStore.update()`, compare old/new settings and mark only syncable settings slices dirty.

- [ ] **Step 3: Run compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds.

## Task 8: Cloud Sync UI

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/BackupPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/BackupVM.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/tabs/CloudSyncTab.kt`

- [ ] **Step 1: Add VM methods**

Expose config/state/status, test connection, and sync-now calls.

- [ ] **Step 2: Add tab UI**

Add Cloud Sync tab before WebDAV Backup with enable switch, WebDAV fields, sync path, status, last sync time, and sync button.

- [ ] **Step 3: Run compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds.

## Task 9: Verification

**Files:**
- All changed files.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.data.sync.cloud.*'
```

Expected: all Cloud Sync tests pass.

- [ ] **Step 2: Run compile**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 3: Inspect git diff**

Run:

```bash
git diff --stat
git diff -- app/src/main/java/me/rerere/rikkahub/data/sync app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt app/src/main/java/me/rerere/rikkahub/service/ChatService.kt app/src/main/java/me/rerere/rikkahub/ui/pages/backup
```

Expected: changes match this plan and do not modify unrelated files.
