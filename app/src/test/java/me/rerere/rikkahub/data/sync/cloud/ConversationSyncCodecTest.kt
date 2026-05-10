package me.rerere.rikkahub.data.sync.cloud

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationSyncCodecTest {
    @Test
    fun `conversation envelope serializes the full conversation payload`() {
        val conversation = conversation(
            title = "sync test",
            chatSuggestions = listOf("next"),
            isPinned = true,
            messageNodes = listOf(MessageNode.of(UIMessage.user("hello"))),
        )
        val envelope = buildConversationEnvelope(
            conversation = conversation,
            version = 7,
            updatedAt = 123,
            deviceId = "phone",
            files = listOf(
                ReferencedFile(
                    uri = "file:///tmp/image.png",
                    hash = "sha256:abc",
                    path = "files/abc",
                    size = 42,
                )
            ),
        )

        val decoded = JsonInstant.decodeFromString<ConversationEnvelope>(
            JsonInstant.encodeToString(envelope)
        )

        assertEquals(1, decoded.schemaVersion)
        assertEquals(conversationObjectKey(conversation.id.toString()), decoded.objectKey)
        assertEquals(7, decoded.version)
        assertEquals(123, decoded.updatedAt)
        assertEquals("phone", decoded.deviceId)
        assertEquals("sync test", decoded.conversation.title)
        assertEquals(listOf("next"), decoded.conversation.chatSuggestions)
        assertEquals(true, decoded.conversation.isPinned)
        assertEquals("hello", decoded.conversation.currentMessages.single().toText())
        assertEquals("sha256:abc", decoded.files.single().hash)
    }

    @Test
    fun `merge keeps concurrent replies as message alternatives without conflict prompts`() {
        val nodeId = Uuid.parse("33333333-3333-3333-3333-333333333333")
        val firstReply = UIMessage.assistant("first").copy(
            id = Uuid.parse("44444444-4444-4444-4444-444444444444")
        )
        val secondReply = UIMessage.assistant("second").copy(
            id = Uuid.parse("55555555-5555-5555-5555-555555555555")
        )
        val local = conversation(
            title = "local",
            messageNodes = listOf(MessageNode(id = nodeId, messages = listOf(firstReply), selectIndex = 0)),
            createAt = Instant.ofEpochMilli(10),
            updateAt = Instant.ofEpochMilli(20),
        )
        val remote = local.copy(
            title = "remote",
            messageNodes = listOf(MessageNode(id = nodeId, messages = listOf(secondReply), selectIndex = 0)),
            createAt = Instant.ofEpochMilli(30),
            updateAt = Instant.ofEpochMilli(40),
        )

        val merged = mergeConversations(local, remote)

        assertEquals("remote", merged.title)
        assertEquals(Instant.ofEpochMilli(10), merged.createAt)
        assertEquals(Instant.ofEpochMilli(40), merged.updateAt)
        val mergedNode = merged.messageNodes.single()
        assertEquals(0, mergedNode.selectIndex)
        assertEquals("second", mergedNode.currentMessage.toText())
        assertEquals(setOf("first", "second"), mergedNode.messages.map { it.toText() }.toSet())
    }

    @Test
    fun `merge replaces the same message id with the newer conversation content`() {
        val nodeId = Uuid.parse("66666666-6666-6666-6666-666666666666")
        val messageId = Uuid.parse("77777777-7777-7777-7777-777777777777")
        val local = conversation(
            messageNodes = listOf(
                MessageNode(
                    id = nodeId,
                    messages = listOf(UIMessage.assistant("old").copy(id = messageId)),
                    selectIndex = 0,
                )
            ),
            updateAt = Instant.ofEpochMilli(20),
        )
        val remote = local.copy(
            messageNodes = listOf(
                MessageNode(
                    id = nodeId,
                    messages = listOf(UIMessage.assistant("new").copy(id = messageId)),
                    selectIndex = 0,
                )
            ),
            updateAt = Instant.ofEpochMilli(40),
        )

        val merged = mergeConversations(local, remote)

        assertEquals(listOf("new"), merged.messageNodes.single().messages.map { it.toText() })
    }

    private fun conversation(
        title: String = "",
        chatSuggestions: List<String> = emptyList(),
        isPinned: Boolean = false,
        messageNodes: List<MessageNode>,
        createAt: Instant = Instant.ofEpochMilli(10),
        updateAt: Instant = Instant.ofEpochMilli(20),
    ): Conversation {
        return Conversation(
            id = Uuid.parse("11111111-1111-1111-1111-111111111111"),
            assistantId = Uuid.parse("22222222-2222-2222-2222-222222222222"),
            title = title,
            messageNodes = messageNodes,
            chatSuggestions = chatSuggestions,
            isPinned = isPinned,
            createAt = createAt,
            updateAt = updateAt,
        )
    }
}
