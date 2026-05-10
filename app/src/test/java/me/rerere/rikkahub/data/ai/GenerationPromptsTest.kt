package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationPromptsTest {

    private fun textOf(message: UIMessage): String =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    @Test
    fun `deepseek should split system prompt into separate messages`() {
        val result = buildSystemMessages(
            assistantPrompt = "assistant",
            memoryPrompt = "memory",
            recentChatsPrompt = "recent",
            toolPrompts = listOf("tool"),
            deepSeek = true,
        )

        assertEquals(4, result.size)
        assertEquals("assistant", textOf(result[0]))
        assertEquals("memory", textOf(result[1]))
        assertEquals("recent", textOf(result[2]))
        assertEquals("tool", textOf(result[3]))
    }

    @Test
    fun `non deepseek should keep a single combined system message`() {
        val result = buildSystemMessages(
            assistantPrompt = "assistant",
            memoryPrompt = "memory",
            recentChatsPrompt = "recent",
            toolPrompts = listOf("tool"),
            deepSeek = false,
        )

        assertEquals(1, result.size)
        val text = textOf(result[0])
        assertTrue(text.contains("assistant"))
        assertTrue(text.contains("memory"))
        assertTrue(text.contains("recent"))
        assertTrue(text.contains("tool"))
    }
}
