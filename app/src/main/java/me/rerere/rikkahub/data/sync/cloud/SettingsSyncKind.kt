package me.rerere.rikkahub.data.sync.cloud

enum class SettingsSyncKind(val remoteName: String, val path: String) {
    Assistants("assistants", "settings/assistants.json"),
    AssistantTags("assistant-tags", "settings/assistant-tags.json"),
    PromptInjections("prompt-injections", "settings/prompt-injections.json"),
    Lorebooks("lorebooks", "settings/lorebooks.json"),
    QuickMessages("quick-messages", "settings/quick-messages.json"),
    ModelSelection("model-selection", "settings/model-selection.json"),
}
