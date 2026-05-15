package me.rerere.rikkahub.data.sync.cloud

import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class PulledConversationVisibilityTest {
    @Test
    fun `pulled conversation assistant is added and selected when current assistant has no conversations`() {
        val pulledAssistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")

        val updated = Settings(
            assistantId = DEFAULT_ASSISTANT_ID,
            assistants = listOf(Assistant(id = DEFAULT_ASSISTANT_ID)),
        ).withConversationAssistantsVisible(
            conversationAssistantIds = listOf(pulledAssistantId),
            selectedAssistantHasConversations = false,
        )

        assertEquals(pulledAssistantId, updated.assistantId)
        assertEquals(
            setOf(DEFAULT_ASSISTANT_ID, pulledAssistantId),
            updated.assistants.mapTo(mutableSetOf()) { it.id },
        )
    }

    @Test
    fun `current assistant is preserved when it already has conversations`() {
        val pulledAssistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")

        val updated = Settings(
            assistantId = DEFAULT_ASSISTANT_ID,
            assistants = listOf(Assistant(id = DEFAULT_ASSISTANT_ID)),
        ).withConversationAssistantsVisible(
            conversationAssistantIds = listOf(pulledAssistantId),
            selectedAssistantHasConversations = true,
        )

        assertEquals(DEFAULT_ASSISTANT_ID, updated.assistantId)
        assertEquals(
            setOf(DEFAULT_ASSISTANT_ID, pulledAssistantId),
            updated.assistants.mapTo(mutableSetOf()) { it.id },
        )
    }

    @Test
    fun `current assistant is preserved when it is among pulled assistants`() {
        val updated = Settings(
            assistantId = DEFAULT_ASSISTANT_ID,
            assistants = listOf(Assistant(id = DEFAULT_ASSISTANT_ID)),
        ).withConversationAssistantsVisible(
            conversationAssistantIds = listOf(DEFAULT_ASSISTANT_ID),
            selectedAssistantHasConversations = false,
        )

        assertEquals(DEFAULT_ASSISTANT_ID, updated.assistantId)
    }

    @Test
    fun `existing hidden conversation assistant is selected after a manual sync with no new pull`() {
        val olderAssistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val newestAssistantId = Uuid.parse("33333333-3333-3333-3333-333333333333")

        val updated = Settings(
            assistantId = DEFAULT_ASSISTANT_ID,
            assistants = listOf(Assistant(id = DEFAULT_ASSISTANT_ID)),
        ).withConversationAssistantsVisible(
            conversationAssistantIds = listOf(newestAssistantId, olderAssistantId),
            selectedAssistantHasConversations = false,
        )

        assertEquals(newestAssistantId, updated.assistantId)
        assertEquals(
            setOf(DEFAULT_ASSISTANT_ID, newestAssistantId, olderAssistantId),
            updated.assistants.mapTo(mutableSetOf()) { it.id },
        )
    }

    @Test
    fun `remote assistant list is not changed when placeholder assistants are disabled`() {
        val remoteAssistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val conversationOnlyAssistantId = Uuid.parse("33333333-3333-3333-3333-333333333333")

        val updated = Settings(
            assistantId = DEFAULT_ASSISTANT_ID,
            assistants = listOf(
                Assistant(id = DEFAULT_ASSISTANT_ID),
                Assistant(id = remoteAssistantId),
            ),
        ).withConversationAssistantsVisible(
            conversationAssistantIds = listOf(conversationOnlyAssistantId, remoteAssistantId),
            selectedAssistantHasConversations = false,
            allowPlaceholderAssistants = false,
        )

        assertEquals(remoteAssistantId, updated.assistantId)
        assertEquals(
            listOf(DEFAULT_ASSISTANT_ID, remoteAssistantId),
            updated.assistants.map { it.id },
        )
    }
}
