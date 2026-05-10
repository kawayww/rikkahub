package me.rerere.rikkahub.data.ai.summary

import java.util.Locale
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_CONVERSATION_SUMMARY_PROMPT
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.applyPlaceholders

internal fun Conversation.pendingSummaryCandidates(): List<UIMessage> {
    return currentMessages.filter { message ->
        message.summary.isNullOrBlank() &&
            message.role != MessageRole.SYSTEM &&
            message.role != MessageRole.TOOL
    }
}

@Suppress("DEPRECATION")
internal fun UIMessage.summarySourceText(): String {
    val textParts = parts.mapNotNull { part ->
        when (part) {
            is UIMessagePart.Text -> part.text.trim().takeIf { it.isNotBlank() }
            is UIMessagePart.Image -> "Image attachment: ${part.url}"
            is UIMessagePart.Video -> "Video attachment: ${part.url}"
            is UIMessagePart.Audio -> "Audio attachment: ${part.url}"
            is UIMessagePart.Document -> "Document attachment: ${part.fileName}"
            is UIMessagePart.Tool -> "Tool call: ${part.toolName}"
            is UIMessagePart.Reasoning -> null
            is UIMessagePart.Search -> null
            is UIMessagePart.ToolCall -> "Tool call: ${part.toolName}"
            is UIMessagePart.ToolResult -> "Tool result: ${part.toolName}"
        }
    }

    if (textParts.isNotEmpty()) {
        return textParts.joinToString("\n")
    }

    return when (role) {
        MessageRole.USER -> "User message"
        MessageRole.ASSISTANT -> "Assistant message"
        MessageRole.SYSTEM -> "System message"
        MessageRole.TOOL -> "Tool message"
    }
}

internal fun buildMessageSummaryPrompt(
    message: UIMessage,
    locale: Locale,
): String {
    return DEFAULT_CONVERSATION_SUMMARY_PROMPT.applyPlaceholders(
        "locale" to locale.displayName,
        "role" to message.role.name.lowercase(Locale.ROOT),
        "content" to message.summarySourceText(),
    )
}

internal fun cleanGeneratedMessageSummary(summary: String): String {
    return summary
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            line
                .replace(Regex("""^[-*+]\s+"""), "")
                .replace(Regex("""^\d+[.)]\s+"""), "")
                .replace(Regex("""(?i)^summary\s*[:：-]\s*"""), "")
                .trim()
        }
        .joinToString(" ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('"', '\'')
}
