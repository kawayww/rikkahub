package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.findProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val DEEPSEEK_RUNTIME_CONTEXT_TAG = "deepseek_runtime_context"
private val DEEPSEEK_VOLATILE_SYSTEM_LINE_PREFIXES = listOf(
    "- Time:",
)

object DeepSeekCacheTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val provider = resolveProvider(ctx.model, ctx.settings.providers) ?: return messages
        if (!isDeepSeekProvider(provider)) return messages
        return stabilizeDeepSeekCachePrefix(messages)
    }
}

internal fun resolveProvider(
    model: Model,
    providers: List<ProviderSetting>,
): ProviderSetting? {
    model.providerOverwrite?.let { return it }
    return model.findProvider(providers)
}

internal fun isDeepSeekProvider(provider: ProviderSetting?): Boolean {
    if (provider !is ProviderSetting.OpenAI) return false
    return provider.baseUrl.toHttpUrlOrNull()?.host?.equals("api.deepseek.com", ignoreCase = true) == true
}

internal fun stabilizeDeepSeekCachePrefix(messages: List<UIMessage>): List<UIMessage> {
    val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
    if (systemIndex < 0) return messages

    val systemMessage = messages[systemIndex]
    val systemText = systemMessage.textContent()
    if (systemText.isBlank()) return messages

    val (stableText, runtimeLines) = splitDeepSeekRuntimeContext(systemText)
    if (runtimeLines.isEmpty()) return messages

    val result = messages.toMutableList()
    if (stableText.isBlank()) {
        result.removeAt(systemIndex)
    } else {
        result[systemIndex] = systemMessage.copy(
            parts = listOf(UIMessagePart.Text(stableText))
        )
    }

    val runtimeContextMessage = buildRuntimeContextMessage(runtimeLines)
    var insertIndex = result.indexOfLast { it.role == MessageRole.USER }
        .takeIf { it >= 0 } ?: result.size
    insertIndex = findSafeInsertIndex(result, insertIndex)
    result.add(insertIndex, runtimeContextMessage)

    return result
}

private fun splitDeepSeekRuntimeContext(text: String): Pair<String, List<String>> {
    val stableLines = mutableListOf<String>()
    val runtimeLines = mutableListOf<String>()

    text.lineSequence().forEach { line ->
        if (isVolatileDeepSeekSystemLine(line)) {
            runtimeLines.add(line)
        } else {
            stableLines.add(line)
        }
    }

    return stableLines.joinToString("\n") to runtimeLines
}

private fun isVolatileDeepSeekSystemLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return DEEPSEEK_VOLATILE_SYSTEM_LINE_PREFIXES.any { prefix ->
        trimmed.startsWith(prefix, ignoreCase = true)
    }
}

private fun buildRuntimeContextMessage(lines: List<String>): UIMessage {
    val content = buildString {
        appendLine("<$DEEPSEEK_RUNTIME_CONTEXT_TAG>")
        lines.forEach { line ->
            appendLine(line)
        }
        append("</$DEEPSEEK_RUNTIME_CONTEXT_TAG>")
    }
    return UIMessage.system(content)
}

private fun UIMessage.textContent(): String {
    return parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
}
