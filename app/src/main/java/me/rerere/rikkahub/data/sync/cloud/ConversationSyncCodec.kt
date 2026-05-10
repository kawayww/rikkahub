package me.rerere.rikkahub.data.sync.cloud

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode

@Serializable
data class ConversationEnvelope(
    val schemaVersion: Int = 1,
    val objectKey: String,
    val version: Long,
    val updatedAt: Long,
    val deviceId: String,
    val conversation: Conversation,
    val files: List<ReferencedFile> = emptyList(),
)

@Serializable
data class ReferencedFile(
    val uri: String,
    val hash: String,
    val path: String,
    val size: Long? = null,
    val displayName: String? = null,
    val mime: String? = null,
)

fun buildConversationEnvelope(
    conversation: Conversation,
    version: Long,
    updatedAt: Long,
    deviceId: String,
    files: List<ReferencedFile> = emptyList(),
): ConversationEnvelope {
    return ConversationEnvelope(
        objectKey = conversationObjectKey(conversation.id.toString()),
        version = version,
        updatedAt = updatedAt,
        deviceId = deviceId,
        conversation = conversation,
        files = files,
    )
}

fun mergeConversations(
    local: Conversation,
    remote: Conversation,
): Conversation {
    val remoteIsNewer = !remote.updateAt.isBefore(local.updateAt)
    val newer = if (remoteIsNewer) remote else local
    val older = if (remoteIsNewer) local else remote

    return newer.copy(
        messageNodes = mergeMessageNodes(newer.messageNodes, older.messageNodes),
        createAt = minOf(local.createAt, remote.createAt),
        updateAt = maxOf(local.updateAt, remote.updateAt),
        newConversation = false,
    )
}

private fun mergeMessageNodes(
    newerNodes: List<MessageNode>,
    olderNodes: List<MessageNode>,
): List<MessageNode> {
    val olderById = olderNodes.associateBy { it.id }
    val newerIds = newerNodes.mapTo(mutableSetOf()) { it.id }

    return newerNodes.map { newerNode ->
        val olderNode = olderById[newerNode.id]
        if (olderNode == null) newerNode else mergeMessageNode(newerNode, olderNode)
    } + olderNodes.filter { it.id !in newerIds }
}

private fun mergeMessageNode(
    newerNode: MessageNode,
    olderNode: MessageNode,
): MessageNode {
    val newerMessageIds = newerNode.messages.mapTo(mutableSetOf()) { it.id }
    val messages = newerNode.messages + olderNode.messages.filter { it.id !in newerMessageIds }

    return newerNode.copy(
        messages = messages,
        selectIndex = newerNode.selectIndex.clipTo(messages.size),
        isFavorite = newerNode.isFavorite || olderNode.isFavorite,
    )
}

private fun Int.clipTo(size: Int): Int {
    return if (size <= 0) 0 else coerceIn(0, size - 1)
}
