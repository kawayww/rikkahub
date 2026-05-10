package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class PreferencesStoreTest {
    @Test
    fun `settings preserve conversation summary model id and overview defaults`() {
        val summaryModelId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val settings = Settings(
            conversationSummaryModelId = summaryModelId,
            displaySetting = DisplaySetting(
                chatOverviewDisplayMode = ChatOverviewDisplayMode.AI_SUMMARY,
                overviewSummaryLines = 1,
            ),
        )

        val encoded = JsonInstant.encodeToString(settings)
        val decoded = JsonInstant.decodeFromString<Settings>(encoded)

        assertEquals(summaryModelId, decoded.conversationSummaryModelId)
        assertEquals(ChatOverviewDisplayMode.AI_SUMMARY, decoded.displaySetting.chatOverviewDisplayMode)
        assertEquals(1, decoded.displaySetting.overviewSummaryLines)
    }
}
