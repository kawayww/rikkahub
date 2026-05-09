package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekCacheTransformerTest {

    private fun textOf(message: UIMessage): String =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    @Test
    fun `deepseek provider should be detected by official base url`() {
        val provider = ProviderSetting.OpenAI(baseUrl = "https://api.deepseek.com/v1")

        assertTrue(isDeepSeekProvider(provider))
    }

    @Test
    fun `non deepseek provider should not be detected`() {
        val provider = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1")

        assertFalse(isDeepSeekProvider(provider))
    }

    @Test
    fun `volatile time line should move into runtime context near latest user message`() {
        val messages = listOf(
            UIMessage.system(
                """
                    You are a helpful assistant.

                    ## Info
                    - Time: 2026-05-10 10:00:00
                    - Locale: English

                    ## Hint
                    - Keep answers concise.
                """.trimIndent()
            ),
            UIMessage.user("first user"),
            UIMessage.assistant("first reply"),
            UIMessage.user("latest user"),
        )

        val result = stabilizeDeepSeekCachePrefix(messages)

        assertEquals(5, result.size)
        assertEquals("You are a helpful assistant.\n\n## Info\n- Locale: English\n\n## Hint\n- Keep answers concise.", textOf(result[0]))
        assertEquals(MessageRole.USER, result[1].role)
        assertEquals("first user", textOf(result[1]))
        assertEquals(MessageRole.ASSISTANT, result[2].role)
        assertEquals(MessageRole.SYSTEM, result[3].role)
        assertTrue(textOf(result[3]).contains("<deepseek_runtime_context>"))
        assertTrue(textOf(result[3]).contains("- Time: 2026-05-10 10:00:00"))
        assertFalse(textOf(result[0]).contains("- Time:"))
        assertEquals("latest user", textOf(result[4]))
    }

    @Test
    fun `messages without volatile system lines should remain unchanged`() {
        val messages = listOf(
            UIMessage.system(
                """
                    You are a helpful assistant.

                    ## Hint
                    - Keep answers concise.
                """.trimIndent()
            ),
            UIMessage.user("hello"),
        )

        val result = stabilizeDeepSeekCachePrefix(messages)

        assertEquals(messages, result)
    }
}
