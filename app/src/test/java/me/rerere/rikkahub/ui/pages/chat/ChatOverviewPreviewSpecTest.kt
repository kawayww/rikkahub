package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.ChatOverviewDisplayMode
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.service.ConversationSummaryProgress
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
            summaryProgress = ConversationSummaryProgress(),
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
            summaryProgress = ConversationSummaryProgress(),
        )
        val normalMode = buildChatOverviewSummaryBackfillPromptSpec(
            conversation = conversation,
            settings = Settings(),
            previewMode = false,
            dismissedConversationId = null,
            summaryProgress = ConversationSummaryProgress(),
        )
        val dismissed = buildChatOverviewSummaryBackfillPromptSpec(
            conversation = conversation,
            settings = Settings(),
            previewMode = true,
            dismissedConversationId = conversation.id,
            summaryProgress = ConversationSummaryProgress(),
        )

        assertFalse(truncatedMode.shouldPrompt)
        assertFalse(normalMode.shouldPrompt)
        assertFalse(dismissed.shouldPrompt)
    }

    @Test
    fun `overview summary backfill prompt is skipped while summaries are generating`() {
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode.of(UIMessage.user("hello")),
                MessageNode.of(UIMessage.assistant("answer")),
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
            summaryProgress = ConversationSummaryProgress(
                running = true,
                completed = 1,
                total = 2,
            ),
        )

        assertFalse(prompt.shouldPrompt)
        assertEquals(2, prompt.missingCount)
    }

    @Test
    fun `overview summary backfill prompt is skipped while summaries are queued`() {
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode.of(UIMessage.user("hello")),
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
            summaryProgress = ConversationSummaryProgress(
                queued = true,
                completed = 0,
                total = 1,
            ),
        )

        assertFalse(prompt.shouldPrompt)
        assertEquals(1, prompt.missingCount)
    }

    @Test
    fun `overview summary backfill prompt is skipped after summary generation failure`() {
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode.of(UIMessage.user("hello")),
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
            summaryProgress = ConversationSummaryProgress(
                running = false,
                completed = 0,
                total = 1,
                errorMessage = "model failed",
            ),
        )

        assertFalse(prompt.shouldPrompt)
        assertEquals(1, prompt.missingCount)
    }

    @Test
    fun `conversation summary progress exposes bounded progress fraction`() {
        val halfDone = ConversationSummaryProgress(
            running = true,
            completed = 2,
            total = 5,
        )
        val overDone = ConversationSummaryProgress(
            running = false,
            completed = 7,
            total = 5,
        )
        val empty = ConversationSummaryProgress(
            running = true,
            completed = 0,
            total = 0,
        )

        assertEquals(0.4f, halfDone.progressFraction!!, 0.0f)
        assertEquals(1.0f, overDone.progressFraction!!, 0.0f)
        assertEquals(null, empty.progressFraction)
        assertTrue(ConversationSummaryProgress(queued = true).active)
        assertTrue(ConversationSummaryProgress(running = true).active)
        assertFalse(ConversationSummaryProgress().active)
    }

    @Test
    fun `visible summary progress shows queued fallback immediately after user request`() {
        val visible = buildVisibleChatOverviewSummaryProgress(
            summaryProgress = ConversationSummaryProgress(),
            summaryRequested = true,
            missingCount = 3,
        )

        assertTrue(visible.queued)
        assertTrue(visible.active)
        assertEquals(0, visible.completed)
        assertEquals(3, visible.total)
    }

    @Test
    fun `visible summary progress keeps service state when service is already active or failed`() {
        val running = ConversationSummaryProgress(
            running = true,
            completed = 1,
            total = 4,
        )
        val failed = ConversationSummaryProgress(
            completed = 1,
            total = 4,
            errorMessage = "failed",
        )

        assertEquals(
            running,
            buildVisibleChatOverviewSummaryProgress(
                summaryProgress = running,
                summaryRequested = true,
                missingCount = 3,
            )
        )
        assertEquals(
            failed,
            buildVisibleChatOverviewSummaryProgress(
                summaryProgress = failed,
                summaryRequested = true,
                missingCount = 3,
            )
        )
    }
}
