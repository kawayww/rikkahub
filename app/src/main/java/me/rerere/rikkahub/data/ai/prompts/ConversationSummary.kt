package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_CONVERSATION_SUMMARY_PROMPT = """
    You summarize one chat message for a compact conversation overview.

    Rules:
    - Summarize in {locale}.
    - Keep it short, factual, and self contained.
    - Do not use markdown, bullets, quotes, labels, or extra commentary.
    - If the message only contains attachments, mention the attachment type and likely intent.
    - Return only the summary text.

    <role>
    {role}
    </role>

    <message>
    {content}
    </message>
""".trimIndent()
