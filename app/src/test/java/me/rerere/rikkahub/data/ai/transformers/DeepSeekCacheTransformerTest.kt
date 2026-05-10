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
        val stableSystem = textOf(result[0])
        val runtimeContext = textOf(result[3])
        assertFalse(stableSystem.contains("## Info"))
        assertFalse(stableSystem.contains("- Locale:"))
        assertTrue(stableSystem.contains("## Hint"))
        assertEquals(MessageRole.USER, result[1].role)
        assertEquals("first user", textOf(result[1]))
        assertEquals(MessageRole.ASSISTANT, result[2].role)
        assertEquals(MessageRole.SYSTEM, result[3].role)
        assertTrue(runtimeContext.contains("<deepseek_runtime_context>"))
        assertTrue(runtimeContext.contains("## Info"))
        assertTrue(runtimeContext.contains("- Time: 2026-05-10 10:00:00"))
        assertEquals("latest user", textOf(result[4]))
    }

    @Test
    fun `info block should move entirely into runtime context`() {
        val messages = listOf(
            UIMessage.system(
                """
                    You are a helpful assistant.

                    ## Info
                    - Time: 2026-05-10 10:00:00
                    - Locale: English
                    - Timezone: China Standard Time
                    - Device Info: Google Pixel
                    - System Version: Android SDK v37
                    - User Nickname: user

                    ## Hint
                    - Keep answers concise.
                """.trimIndent()
            ),
            UIMessage.user("hello"),
        )

        val result = stabilizeDeepSeekCachePrefix(messages)

        assertEquals(3, result.size)
        val stableSystem = textOf(result[0])
        val runtimeContext = textOf(result[1])
        assertFalse(stableSystem.contains("## Info"))
        assertFalse(stableSystem.contains("- Locale:"))
        assertFalse(stableSystem.contains("- User Nickname:"))
        assertTrue(stableSystem.contains("## Hint"))
        assertTrue(runtimeContext.contains("## Info"))
        assertTrue(runtimeContext.contains("- Locale: English"))
        assertTrue(runtimeContext.contains("- User Nickname: user"))
        assertEquals("hello", textOf(result[2]))
    }

    @Test
    fun `volatile lines in later leading system messages should also move into runtime context`() {
        val messages = listOf(
            UIMessage.system(
                """
                    You are a helpful assistant.

                    ## Hint
                    - Keep answers concise.
                """.trimIndent()
            ),
            UIMessage.system(
                """
                    Memory context

                    ## Info
                    - Time: 2026-05-10 10:00:00
                    - Locale: English

                    ## Note
                    - Keep stable output.
                    Today is 2026-05-10.
                """.trimIndent()
            ),
            UIMessage.user("hello"),
        )

        val result = stabilizeDeepSeekCachePrefix(messages)

        assertEquals(4, result.size)
        assertEquals("You are a helpful assistant.\n\n## Hint\n- Keep answers concise.", textOf(result[0]))

        val stableSystem = textOf(result[1])
        assertFalse(stableSystem.contains("## Info"))
        assertFalse(stableSystem.contains("- Time:"))
        assertFalse(stableSystem.contains("Today is"))
        assertTrue(stableSystem.contains("Memory context"))
        assertTrue(stableSystem.contains("## Note"))
        assertTrue(stableSystem.contains("- Keep stable output."))

        val runtimeContext = textOf(result[2])
        assertTrue(runtimeContext.contains("<deepseek_runtime_context>"))
        assertTrue(runtimeContext.contains("## Info"))
        assertTrue(runtimeContext.contains("- Locale: English"))
        assertTrue(runtimeContext.contains("Today is 2026-05-10."))
        assertEquals("hello", textOf(result[3]))
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
