package com.cristianku.nav2pilot.places

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * OpenStreetMap's opening_hours, the part almost every shop uses: weekdays ("Mo-Fr", "Sa,Su", "Fr-Mo"), times
 * ("08:00-12:00,13:30-18:00", past midnight as "22:00-02:00"), "24/7", "off" or "closed", rules separated by ";"
 * where a later rule replaces the earlier ones on its days. Public and school holidays (PH, SH) are left out.
 *
 * Anything else (months, dates, sunrise, open ends, comments) makes [parse] give null: the app then shows the text
 * as OpenStreetMap has it, rather than a guess.
 */
class OpeningHours private constructor(
    /** For each weekday from Monday, its openings in minutes from its midnight; an end past 1440 is the next day. */
    private val days: List<List<Span>>,
) {
    // the whole week in minutes from Monday 00:00, an opening over midnight as one; Sunday night goes on to Monday
    private val week: List<Span> = weekSpans(days)

    fun isAlwaysOpen(): Boolean {
        return week.size == 1 && week[0].start == 0 && week[0].end == WEEK_MINUTES
    }

    /** Open or closed at [now], and for how long. */
    fun status(now: LocalDateTime): Status {
        if (isAlwaysOpen()) {
            return Status(open = true, changeInMinutes = null)
        }
        if (week.isEmpty()) {
            return Status(open = false, changeInMinutes = null)
        }
        val minute = (now.dayOfWeek.value - 1) * DAY_MINUTES + now.hour * 60 + now.minute
        for (span in week) {
            if (minute >= span.start && minute < span.end) {
                var end = span.end
                // open on Sunday night: until Monday's first closing
                if (end == WEEK_MINUTES && week[0].start == 0) {
                    end = WEEK_MINUTES + week[0].end
                }
                return Status(open = true, changeInMinutes = end - minute)
            }
        }
        for (span in week) {
            if (span.start > minute) {
                return Status(open = false, changeInMinutes = span.start - minute)
            }
        }
        return Status(open = false, changeInMinutes = WEEK_MINUTES - minute + week[0].start)
    }

    /** "Open · Closes 20:00", "Closed · Opens Mon 10:00" and the like, the times as the phone writes them. */
    fun statusText(now: LocalDateTime, locale: Locale = Locale.getDefault()): StatusText {
        val status = status(now)
        val inMinutes = status.changeInMinutes
        if (inMinutes == null) {
            if (status.open) {
                return StatusText(StatusText.Tone.OPEN, "Open 24 hours", "")
            }
            return StatusText(StatusText.Tone.CLOSED, "Closed", "")
        }
        val change = now.plusMinutes(inMinutes.toLong())
        val time = time(change.hour * 60 + change.minute, locale)
        val day = SHORT_DAYS[change.dayOfWeek.value - 1]
        if (status.open) {
            if (inMinutes <= SOON_MINUTES) {
                return StatusText(StatusText.Tone.SOON, "Closes soon", time)
            }
            // closing after midnight is still tonight's closing
            if (inMinutes < DAY_MINUTES) {
                return StatusText(StatusText.Tone.OPEN, "Open", "Closes $time")
            }
            return StatusText(StatusText.Tone.OPEN, "Open", "Closes $day $time")
        }
        if (inMinutes <= SOON_MINUTES) {
            return StatusText(StatusText.Tone.SOON, "Opens soon", time)
        }
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), change.toLocalDate())
        if (days == 0L) {
            return StatusText(StatusText.Tone.CLOSED, "Closed", "Opens $time")
        }
        if (days == 1L) {
            return StatusText(StatusText.Tone.CLOSED, "Closed", "Opens tomorrow $time")
        }
        return StatusText(StatusText.Tone.CLOSED, "Closed", "Opens $day $time")
    }

    /** Monday to Sunday: the day's name and its times, "10:00–20:00", "Closed" or "Open 24 hours". */
    fun week(locale: Locale = Locale.getDefault()): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        for (day in 0 until 7) {
            val spans = days[day]
            var text = "Closed"
            if (spans.size == 1 && spans[0].start == 0 && spans[0].end >= DAY_MINUTES) {
                text = "Open 24 hours"
            } else if (spans.isNotEmpty()) {
                text = spans.joinToString(", ") { span -> time(span.start, locale) + "–" + time(span.end, locale) }
            }
            rows.add(DAYS[day] to text)
        }
        return rows
    }

    /** Open or closed, and in how many minutes that changes; null when it never does. */
    data class Status(val open: Boolean, val changeInMinutes: Int?)

    /** [headline] in the colour of [tone], then [rest]: "Open" and "Closes 20:00". */
    data class StatusText(val tone: Tone, val headline: String, val rest: String) {
        enum class Tone { OPEN, SOON, CLOSED }

        override fun toString(): String {
            if (rest.isEmpty()) {
                return headline
            }
            return "$headline · $rest"
        }
    }

    /** From [start] to [end] minutes, the end not included. */
    private data class Span(val start: Int, val end: Int)

    companion object {
        private const val DAY_MINUTES = 24 * 60
        private const val WEEK_MINUTES = 7 * DAY_MINUTES

        // within an hour of closing or opening, as Google Maps says it
        private const val SOON_MINUTES = 60

        private val DAY_CODES = listOf("mo", "tu", "we", "th", "fr", "sa", "su")
        private val HOLIDAY_CODES = listOf("ph", "sh")
        private val SHORT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        private val TOKEN = Regex("""24/7|\d{1,2}:\d{2}|[A-Za-z]+|\|\||\S""")

        /** The opening hours in [text], or null when it uses more of the format than this app reads. */
        fun parse(text: String): OpeningHours? {
            if (text.isBlank()) {
                return null
            }
            val days = List(7) { mutableListOf<Span>() }
            val tokens = TOKEN.findAll(text).map { match -> match.value }.toList()
            var rule = mutableListOf<String>()
            // "||" is a fallback rule: read like the others
            for (token in tokens + ";") {
                if (token == ";" || token == "||") {
                    if (rule.isNotEmpty() && !RuleReader(rule).applyTo(days)) {
                        return null
                    }
                    rule = mutableListOf()
                } else {
                    rule.add(token)
                }
            }
            return OpeningHours(days.map { spans -> spans.sortedBy { span -> span.start } })
        }

        /** The openings of the whole week from Monday 00:00, overlapping and touching ones merged. */
        private fun weekSpans(days: List<List<Span>>): List<Span> {
            val spans = mutableListOf<Span>()
            for (day in 0 until 7) {
                for (span in days[day]) {
                    val start = day * DAY_MINUTES + span.start
                    val end = day * DAY_MINUTES + span.end
                    if (end > WEEK_MINUTES) {
                        spans.add(Span(start, WEEK_MINUTES))
                        spans.add(Span(0, end - WEEK_MINUTES))
                    } else {
                        spans.add(Span(start, end))
                    }
                }
            }
            spans.sortBy { span -> span.start }
            val merged = mutableListOf<Span>()
            for (span in spans) {
                val last = merged.lastOrNull()
                if (last != null && span.start <= last.end) {
                    merged[merged.size - 1] = Span(last.start, maxOf(last.end, span.end))
                } else {
                    merged.add(span)
                }
            }
            return merged
        }

        private fun time(minuteOfDay: Int, locale: Locale): String {
            val minute = minuteOfDay % DAY_MINUTES
            val text = LocalTime.of(minute / 60, minute % 60)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            // the midnight that ends a day: "20:00–24:00", not "20:00–00:00"
            if (minuteOfDay == DAY_MINUTES && text.matches(Regex("0?0:00"))) {
                return "24:00"
            }
            return text
        }

        /**
         * One rule between semicolons. Its first part replaces the times of its days; a part after a comma that
         * starts with a day ("Mo-Fr 08:00-12:00, Sa 09:00-12:00") adds to the times of its own.
         */
        private class RuleReader(private val tokens: List<String>) {
            private var index = 0

            /** False when the rule cannot be read. */
            fun applyTo(days: List<MutableList<Span>>): Boolean {
                var first = true
                while (true) {
                    val part = readPart() ?: return false
                    for (day in part.days) {
                        if (first) {
                            days[day].clear()
                        }
                        days[day].addAll(part.spans)
                    }
                    if (index >= tokens.size) {
                        return true
                    }
                    if (tokens[index] != "," || !isDay(peek(1))) {
                        return false
                    }
                    index++
                    first = false
                }
            }

            private class Part(val days: List<Int>, val spans: List<Span>)

            private fun readPart(): Part? {
                // the days: none at all means every day; holidays alone, none the app can tell
                val days = mutableListOf<Int>()
                var named = false
                while (isDay(peek(0))) {
                    named = true
                    val code = tokens[index].lowercase(Locale.ROOT)
                    index++
                    if (HOLIDAY_CODES.contains(code)) {
                        // nothing
                    } else if (peek(0) == "-" && DAY_CODES.contains(peek(1)?.lowercase(Locale.ROOT))) {
                        val end = DAY_CODES.indexOf(tokens[index + 1].lowercase(Locale.ROOT))
                        index += 2
                        var day = DAY_CODES.indexOf(code)
                        days.add(day)
                        while (day != end) {
                            day = (day + 1) % 7
                            days.add(day)
                        }
                    } else {
                        days.add(DAY_CODES.indexOf(code))
                    }
                    // a comma between days, not the one before the next part's days after some times
                    if (peek(0) == "," && isDay(peek(1))) {
                        index++
                    } else {
                        break
                    }
                }
                if (!named) {
                    days.addAll(0 until 7)
                }
                // "Mo-Fr: 08:00-18:00" is often written so
                if (peek(0) == ":") {
                    index++
                }

                val spans = mutableListOf<Span>()
                if (peek(0) == "24/7") {
                    index++
                    spans.add(Span(0, DAY_MINUTES))
                } else if (isTime(peek(0))) {
                    while (true) {
                        val span = readSpan() ?: return null
                        spans.add(span)
                        if (peek(0) == "," && isTime(peek(1))) {
                            index++
                        } else {
                            break
                        }
                    }
                }

                val word = peek(0)?.lowercase(Locale.ROOT)
                if (word == "off" || word == "closed") {
                    index++
                    // closing a part of a day is rare, and not worth a guess
                    if (spans.isNotEmpty()) {
                        return null
                    }
                    return Part(days.distinct(), emptyList())
                }
                if (word == "open") {
                    index++
                }
                if (spans.isEmpty()) {
                    // "Mo-Fr" alone is the whole day; a rule with neither days nor times is not one this app reads
                    if (!named) {
                        return null
                    }
                    spans.add(Span(0, DAY_MINUTES))
                }
                return Part(days.distinct(), spans)
            }

            private fun readSpan(): Span? {
                if (!isTime(peek(0)) || peek(1) != "-" || !isTime(peek(2))) {
                    return null
                }
                val start = minutes(tokens[index])
                var end = minutes(tokens[index + 2])
                index += 3
                if (start == null || end == null || start >= DAY_MINUTES || end > 2 * DAY_MINUTES) {
                    return null
                }
                if (end <= start) {
                    end += DAY_MINUTES
                }
                return Span(start, end)
            }

            private fun peek(ahead: Int): String? {
                return tokens.getOrNull(index + ahead)
            }

            private fun isDay(token: String?): Boolean {
                val code = token?.lowercase(Locale.ROOT) ?: return false
                return DAY_CODES.contains(code) || HOLIDAY_CODES.contains(code)
            }

            private fun isTime(token: String?): Boolean {
                return token != null && token.length >= 4 && token[0].isDigit() && token.contains(':')
            }

            private fun minutes(time: String): Int? {
                val parts = time.split(':')
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                if (minute >= 60) {
                    return null
                }
                return hour * 60 + minute
            }
        }
    }
}
