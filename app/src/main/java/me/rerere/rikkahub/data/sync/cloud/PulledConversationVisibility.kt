package me.rerere.rikkahub.data.sync.cloud

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

internal fun Settings.withConversationAssistantsVisible(
    conversationAssistantIds: List<Uuid>,
    selectedAssistantHasConversations: Boolean,
    allowPlaceholderAssistants: Boolean = true,
): Settings {
    if (conversationAssistantIds.isEmpty()) return this

    val orderedConversationAssistantIds = conversationAssistantIds.distinct()
    val existingAssistantIds = assistants.mapTo(mutableSetOf()) { it.id }
    val selectableAssistantIds = if (allowPlaceholderAssistants) {
        orderedConversationAssistantIds
    } else {
        orderedConversationAssistantIds.filter { it in existingAssistantIds }
    }
    if (selectableAssistantIds.isEmpty()) return this

    val mergedAssistants = if (allowPlaceholderAssistants) {
        assistants + orderedConversationAssistantIds
            .filter { it !in existingAssistantIds }
            .map { Assistant(id = it) }
    } else {
        assistants
    }

    return copy(
        assistants = mergedAssistants,
        assistantId = if (selectedAssistantHasConversations) {
            assistantId
        } else if (assistantId in selectableAssistantIds) {
            assistantId
        } else {
            selectableAssistantIds.first()
        },
    )
}
