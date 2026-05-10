package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.ChatOverviewDisplayMode
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatOverviewPreviewSpecTest {
    @Test
    fun `summary mode prefers cached summary over raw text`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("raw text")),
            summary = "cached summary",
        )
        val settings = Settings(
            displaySetting = DisplaySetting(
                chatOverviewDisplayMode = ChatOverviewDisplayMode.AI_SUMMARY,
            ),
        )

        val spec = buildChatOverviewPreviewSpec(message, settings)

        assertEquals("cached summary", spec.text)
        assertEquals(0.7f, spec.maxWidthFraction, 0.0f)
    }

    @Test
    fun `summary mode normalizes multiline summaries for overview wrapping`() {
        val message = UIMessage.assistant("raw text").copy(
            summary = """
                Summary: first line
                - second line
            """.trimIndent()
        )
        val settings = Settings(
            displaySetting = DisplaySetting(
                chatOverviewDisplayMode = ChatOverviewDisplayMode.AI_SUMMARY,
            ),
        )

        val spec = buildChatOverviewPreviewSpec(message, settings)

        assertEquals("first line second line", spec.text)
    }

    @Test
    fun `truncated mode uses raw text and keeps configured lines`() {
        val message = UIMessage.user("raw text").copy(summary = "cached summary")
        val settings = Settings(
            displaySetting = DisplaySetting(
                chatOverviewDisplayMode = ChatOverviewDisplayMode.TRUNCATED,
                overviewSummaryLines = 2,
            ),
        )

        val spec = buildChatOverviewPreviewSpec(message, settings)

        assertEquals("raw text", spec.text)
        assertEquals(0.7f, spec.maxWidthFraction, 0.0f)
        assertEquals(2, spec.maxLines)
    }

    @Test
    fun `overview summary backfill prompt counts unsummarized current messages`() {
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode.of(UIMessage.user("hello").copy(summary = "cached")),
                MessageNode.of(UIMessage.assistant("answer")),
                MessageNode.of(UIMessage.system("system prompt")),
            ),
        )

        val prompt = buildChatOverviewSummaryBackfillPromptSpec(
            conversation = conversation,
            settings = Settings(
                displaySetting = DisplaySetting(
                    chatOverviewDisplayMode = ChatOverviewDisplayMode.AI_SUMMARY,
                )
            ),
            previewMode = true,
            dismissedConversationId = null,
        )

        assertTrue(prompt.shouldPrompt)
        assertEquals(1, prompt.missingCount)
    }

    @Test
    fun `overview summary backfill prompt is skipped outside ai summary preview mode`() {
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode.of(UIMessage.user("hello")),
            ),
        )

        val truncatedMode = buildChatOverviewSummaryBackfillPromptSpec(
            conversation = conversation,
            settings = Settings(
                displaySetting = DisplaySetting(
                    chatOverviewDisplayMode = ChatOverviewDisplayMode.TRUNCATED,
                )
            ),
            previewMode = true,
            dismissedConversationId = null,
        )
        val normalMode = buildChatOverviewSummaryBackfillPromptSpec(
            conversation = conversation,
            settings = Settings(),
            previewMode = false,
            dismissedConversationId = null,
        )
        val dismissed = buildChatOverviewSummaryBackfillPromptSpec(
            conversation = conversation,
            settings = Settings(),
            previewMode = true,
            dismissedConversationId = conversation.id,
        )

        assertFalse(truncatedMode.shouldPrompt)
        assertFalse(normalMode.shouldPrompt)
        assertFalse(dismissed.shouldPrompt)
    }
}
