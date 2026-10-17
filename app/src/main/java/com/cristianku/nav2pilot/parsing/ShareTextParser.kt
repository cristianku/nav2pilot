package com.cristianku.nav2pilot.parsing

/**
 * What another app shared with us (Intent.EXTRA_TEXT / EXTRA_SUBJECT), split into a link and
 * the surrounding text lines.
 *
 * Google Maps typically shares the place name and address as text lines followed by a
 * maps.app.goo.gl link; this is observed behaviour, not a documented format, so every part is optional.
 */
data class SharedText(
    val link: String,
    val title: String,
    val details: String,
    val textWithoutLink: String,
)

object ShareTextParser {
    private val linkPattern = Regex("""(?:https?://|geo:|google\.navigation:)[^\s<>"]+""", RegexOption.IGNORE_CASE)

    fun parse(text: String, subject: String): SharedText {
        val match = linkPattern.find(text)
        var link = ""
        var remaining = text
        if (match != null) {
            link = match.value.trimEnd('.', ',', ';')
            remaining = text.removeRange(match.range)
        }

        val lines = mutableListOf<String>()
        for (line in remaining.lines()) {
            val cleaned = line.trim()
            if (cleaned.isNotEmpty()) {
                lines.add(cleaned)
            }
        }

        val title: String
        var details = ""
        if (lines.isNotEmpty()) {
            title = lines[0]
            details = lines.drop(1).joinToString(", ")
        } else {
            title = subject.trim()
        }

        return SharedText(
            link = link,
            title = title,
            details = details,
            textWithoutLink = lines.joinToString(", "),
        )
    }
}
