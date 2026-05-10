package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.summary.cleanGeneratedMessageSummary
import me.rerere.rikkahub.data.ai.summary.pendingSummaryCandidates
import me.rerere.rikkahub.data.datastore.ChatOverviewDisplayMode
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ConversationSummaryProgress
import kotlin.uuid.Uuid

internal data class ChatOverviewPreviewSpec(
    val text: String,
    val searchText: String,
    val maxWidthFraction: Float,
    val maxLines: Int,
)

internal data class ChatOverviewSummaryBackfillPromptSpec(
    val shouldPrompt: Boolean,
    val missingCount: Int,
)

internal fun buildVisibleChatOverviewSummaryProgress(
    summaryProgress: ConversationSummaryProgress,
    summaryRequested: Boolean,
    missingCount: Int,
): ConversationSummaryProgress {
    return if (
        summaryRequested &&
        missingCount > 0 &&
        !summaryProgress.active &&
        !summaryProgress.failed
    ) {
        ConversationSummaryProgress(
            queued = true,
            completed = 0,
            total = missingCount,
        )
    } else {
        summaryProgress
    }
}

internal fun buildChatOverviewSummaryBackfillPromptSpec(
    conversation: Conversation,
    settings: Settings,
    previewMode: Boolean,
    dismissedConversationId: Uuid?,
    summaryProgress: ConversationSummaryProgress,
): ChatOverviewSummaryBackfillPromptSpec {
    val missingCount = if (settings.displaySetting.chatOverviewDisplayMode == ChatOverviewDisplayMode.AI_SUMMARY) {
        conversation.pendingSummaryCandidates().size
    } else {
        0
    }

    return ChatOverviewSummaryBackfillPromptSpec(
        shouldPrompt = previewMode &&
            missingCount > 0 &&
            dismissedConversationId != conversation.id &&
            !summaryProgress.active &&
            !summaryProgress.failed,
        missingCount = missingCount,
    )
}

internal fun buildChatOverviewPreviewSpec(
    message: UIMessage,
    settings: Settings,
): ChatOverviewPreviewSpec {
    val rawText = message.toText().trim()
    val summaryText = cleanGeneratedMessageSummary(message.summary.orEmpty())
    val text = when (settings.displaySetting.chatOverviewDisplayMode) {
        ChatOverviewDisplayMode.AI_SUMMARY -> summaryText.ifBlank { rawText }
        ChatOverviewDisplayMode.TRUNCATED -> rawText
    }.ifBlank {
        "[...]"
    }

    val searchText = buildList {
        if (summaryText.isNotBlank()) {
            add(summaryText)
        }
        if (rawText.isNotBlank() && rawText != summaryText) {
            add(rawText)
        }
    }.joinToString("\n").ifBlank { text }

    return ChatOverviewPreviewSpec(
        text = text,
        searchText = searchText,
        maxWidthFraction = 0.7f,
        maxLines = settings.displaySetting.overviewSummaryLines.coerceIn(1, 4),
    )
}
