package me.rerere.rikkahub.data.ai.summary

import java.util.Locale
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class MessageSummaryPlannerTest {
    @Test
    fun `summary candidates skip system and already summarized messages`() {
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode.of(UIMessage.system("system prompt")),
                MessageNode.of(UIMessage.user("hello").copy(summary = "cached")),
                MessageNode.of(UIMessage.assistant("answer")),
            ),
        )

        val candidates = conversation.pendingSummaryCandidates()

        assertEquals(1, candidates.size)
        assertEquals(MessageRole.ASSISTANT, candidates.single().role)
    }

    @Test
    fun `attachment only messages still build a non blank summary source`() {
        val message = UIMessage(
            role = MessageRole.USER,
            parts = listOf(
                UIMessagePart.Image(url = "file:///tmp/pic.png"),
            ),
        )

        val source = message.summarySourceText()

        assertTrue(source.isNotBlank())
        assertTrue(source.contains("image", ignoreCase = true))
    }

    @Test
    fun `summary prompt includes the message content and locale`() {
        val message = UIMessage.user("Please summarize this")

        val prompt = buildMessageSummaryPrompt(
            message = message,
            locale = Locale.CHINESE,
        )

        assertTrue(prompt.contains("Please summarize this"))
        assertTrue(
            prompt.contains("Chinese", ignoreCase = true) ||
                prompt.contains("中文")
        )
    }

    @Test
    fun `generated summary is cleaned for overview display`() {
        val summary = cleanGeneratedMessageSummary(
            """
                Summary: First line
                - second line
                1. third line
            """.trimIndent()
        )

        assertEquals("First line second line third line", summary)
    }
}
