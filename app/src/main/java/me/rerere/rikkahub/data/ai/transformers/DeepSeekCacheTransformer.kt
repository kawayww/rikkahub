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
    "Today is ",
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
    val systemBlock = messages.leadingSystemBlock() ?: return messages

    val runtimeLines = mutableListOf<String>()
    val result = mutableListOf<UIMessage>()
    var changed = false

    messages.forEachIndexed { index, message ->
        if (index !in systemBlock) {
            result.add(message)
            return@forEachIndexed
        }

        val systemText = message.textContent()
        if (systemText.isBlank()) {
            result.add(message)
            return@forEachIndexed
        }

        val (stableText, volatileLines) = splitDeepSeekRuntimeContext(systemText)
        if (volatileLines.isEmpty()) {
            result.add(message)
            return@forEachIndexed
        }

        changed = true
        runtimeLines.addAll(volatileLines)
        if (stableText.isNotBlank()) {
            result.add(
                message.copy(
                    parts = listOf(UIMessagePart.Text(stableText))
                )
            )
        }
    }

    if (!changed) return messages

    val runtimeContextMessage = buildRuntimeContextMessage(runtimeLines)
    var insertIndex = result.indexOfLast { it.role == MessageRole.USER }
        .takeIf { it >= 0 } ?: result.size
    insertIndex = findSafeInsertIndex(result, insertIndex)
    result.add(insertIndex, runtimeContextMessage)

    return result
}

private fun splitDeepSeekRuntimeContext(text: String): Pair<String, List<String>> {
    val runtimeLines = mutableListOf<String>()
    val remainingLines = text.lineSequence().toMutableList()

    extractMarkdownSection(remainingLines, "## Info")?.let { section ->
        runtimeLines.addAll(section.lines)
        remainingLines.subList(section.start, section.endExclusive).clear()
    }

    val stableLines = mutableListOf<String>()
    remainingLines.forEach { line ->
        if (isVolatileDeepSeekSystemLine(line)) {
            runtimeLines.add(line)
        } else {
            stableLines.add(line)
        }
    }

    if (runtimeLines.isEmpty()) {
        return text to emptyList()
    }

    return normalizePromptLines(stableLines) to runtimeLines
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

private fun extractMarkdownSection(
    lines: List<String>,
    heading: String
): MarkdownSection? {
    val start = lines.indexOfFirst { it.trim() == heading }
    if (start < 0) return null

    var end = start + 1
    while (end < lines.size && !isMarkdownHeading(lines[end])) {
        end++
    }

    return MarkdownSection(
        start = start,
        endExclusive = end,
        lines = lines.subList(start, end),
    )
}

private fun isMarkdownHeading(line: String): Boolean {
    return line.trimStart().startsWith("#")
}

private fun normalizePromptLines(lines: List<String>): String {
    val normalized = mutableListOf<String>()
    var previousBlank = false

    lines.forEach { line ->
        val blank = line.isBlank()
        if (blank) {
            if (!previousBlank) {
                normalized.add("")
            }
        } else {
            normalized.add(line)
        }
        previousBlank = blank
    }

    while (normalized.isNotEmpty() && normalized.first().isBlank()) {
        normalized.removeAt(0)
    }
    while (normalized.isNotEmpty() && normalized.last().isBlank()) {
        normalized.removeAt(normalized.lastIndex)
    }

    return normalized.joinToString("\n")
}

private fun List<UIMessage>.leadingSystemBlock(): IntRange? {
    val start = indexOfFirst { it.role == MessageRole.SYSTEM }
    if (start < 0) return null

    var end = start
    while (end + 1 <= lastIndex && this[end + 1].role == MessageRole.SYSTEM) {
        end++
    }

    return start..end
}

private data class MarkdownSection(
    val start: Int,
    val endExclusive: Int,
    val lines: List<String>,
)
