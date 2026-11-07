package com.cristianku.nav2pilot.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

// 9 November 2026 is a Monday
private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime {
    return LocalDateTime.of(2026, 11, 9 + day, hour, minute)
}

private const val MONDAY = 0
private const val WEDNESDAY = 2
private const val THURSDAY = 3
private const val FRIDAY = 4
private const val SATURDAY = 5
private const val SUNDAY = 6

private val SWISS = Locale.forLanguageTag("de-CH")

// IKEA Pratteln in OpenStreetMap, 7 November 2026
private const val IKEA = "Mo-We 10:00-20:00; Th-Fr 10:00-21:00; Sa 09:00-20:00"

class OpeningHoursTest {
    private fun text(hours: String, now: LocalDateTime): String {
        return OpeningHours.parse(hours)!!.statusText(now, SWISS).toString()
    }

    @Test
    fun openUntilTheEveningThenSoonClosing() {
        assertEquals("Open · Closes 20:00", text(IKEA, at(SATURDAY, 15)))
        assertEquals("Closes soon · 20:00", text(IKEA, at(SATURDAY, 19, 30)))
        assertEquals("Open · Closes 21:00", text(IKEA, at(THURSDAY, 10)))
    }

    @Test
    fun closedSaysWhenItOpens() {
        // Saturday night: nothing on Sunday
        assertEquals("Closed · Opens Mon 10:00", text(IKEA, at(SATURDAY, 21)))
        assertEquals("Closed · Opens tomorrow 10:00", text(IKEA, at(SUNDAY, 22)))
        assertEquals("Closed · Opens 10:00", text(IKEA, at(MONDAY, 8)))
        assertEquals("Opens soon · 10:00", text(IKEA, at(MONDAY, 9, 30)))
    }

    @Test
    fun theWeekFromMonday() {
        val week = OpeningHours.parse(IKEA)!!.week(SWISS)

        assertEquals(7, week.size)
        assertEquals("Monday" to "10:00–20:00", week[MONDAY])
        assertEquals("Thursday" to "10:00–21:00", week[THURSDAY])
        assertEquals("Saturday" to "09:00–20:00", week[SATURDAY])
        assertEquals("Sunday" to "Closed", week[SUNDAY])
    }

    @Test
    fun alwaysOpen() {
        for (hours in listOf("24/7", "Mo-Su 00:00-24:00", "24/7; PH off")) {
            val parsed = OpeningHours.parse(hours)!!
            assertTrue(hours, parsed.isAlwaysOpen())
            assertEquals("Open 24 hours", parsed.statusText(at(WEDNESDAY, 3), SWISS).toString())
            assertEquals("Open 24 hours", parsed.week(SWISS)[SUNDAY].second)
        }
    }

    @Test
    fun pastMidnightIsTheSameEvening() {
        val bar = "Fr-Sa 18:00-02:00"

        assertEquals("Open · Closes 02:00", text(bar, at(FRIDAY, 23)))
        // Saturday 00:30 is still Friday's night
        assertEquals("Open · Closes 02:00", text(bar, at(SATURDAY, 0, 30)))
        assertEquals("Closed · Opens 18:00", text(bar, at(SATURDAY, 3)))
        assertEquals("Friday" to "18:00–02:00", OpeningHours.parse(bar)!!.week(SWISS)[FRIDAY])
    }

    @Test
    fun sundayNightGoesOnToMonday() {
        assertEquals("Open · Closes 02:00", text("Su 22:00-02:00", at(MONDAY, 0, 30)))
        // Sunday until midnight and Monday from midnight: one opening
        assertEquals("Open · Closes 08:00", text("Mo 00:00-08:00; Su 20:00-24:00", at(SUNDAY, 21)))
        assertEquals("Sunday" to "20:00–24:00", OpeningHours.parse("Mo 00:00-08:00; Su 20:00-24:00")!!.week(SWISS)[SUNDAY])
    }

    @Test
    fun openUntilAnotherDayNamesIt() {
        assertEquals("Open · Closes Wed 18:00", text("Mo 08:00-24:00; Tu 00:00-24:00; We 00:00-18:00", at(MONDAY, 9)))
    }

    @Test
    fun aLaterRuleReplacesTheEarlierOnItsDays() {
        val week = OpeningHours.parse("Mo-Fr 08:00-18:00; We off; Fr 08:00-12:00")!!.week(SWISS)

        assertEquals("08:00–18:00", week[MONDAY].second)
        assertEquals("Closed", week[WEDNESDAY].second)
        assertEquals("08:00–12:00", week[FRIDAY].second)
    }

    @Test
    fun aCommaBeforeADayAddsARule() {
        val week = OpeningHours.parse("Mo-Fr 08:00-12:00, Sa 09:00-11:00")!!.week(SWISS)

        assertEquals("08:00–12:00", week[FRIDAY].second)
        assertEquals("09:00–11:00", week[SATURDAY].second)
        assertEquals("Closed", week[SUNDAY].second)
    }

    @Test
    fun aLunchBreak() {
        val hours = "Mo-Fr 08:00-12:00,13:30-18:00; Sa 08:00-12:00"

        assertEquals("Closed · Opens 13:30", text(hours, at(MONDAY, 12, 15)))
        assertEquals("Opens soon · 13:30", text(hours, at(MONDAY, 12, 45)))
        assertEquals("08:00–12:00, 13:30–18:00", OpeningHours.parse(hours)!!.week(SWISS)[MONDAY].second)
    }

    @Test
    fun holidaysAreLeftOut() {
        val week = OpeningHours.parse("Mo-Sa 09:00-21:00; PH closed")!!.week(SWISS)
        assertEquals("09:00–21:00", week[SATURDAY].second)

        val sundays = OpeningHours.parse("Mo-Sa 07:00-20:00; Su,PH off")!!.week(SWISS)
        assertEquals("07:00–20:00", sundays[SATURDAY].second)
        assertEquals("Closed", sundays[SUNDAY].second)

        assertEquals("07:00–20:00", OpeningHours.parse("Mo-Su 07:00-20:00; PH 10:00-16:00")!!.week(SWISS)[SUNDAY].second)
    }

    @Test
    fun commonLooseSpellings() {
        assertEquals("08:00–17:00", OpeningHours.parse("mo-fr: 08:00-17:00")!!.week(SWISS)[MONDAY].second)
        assertEquals("08:00–17:00", OpeningHours.parse("Mo-Fr 08:00-17:00 open")!!.week(SWISS)[MONDAY].second)
        // days across the weekend
        val weekend = OpeningHours.parse("Fr-Mo 10:00-16:00")!!.week(SWISS)
        assertEquals("10:00–16:00", weekend[SUNDAY].second)
        assertEquals("10:00–16:00", weekend[MONDAY].second)
        assertEquals("Closed", weekend[WEDNESDAY].second)
        // times alone: every day
        assertEquals("06:00–22:00", OpeningHours.parse("06:00-22:00")!!.week(SWISS)[SUNDAY].second)
    }

    @Test
    fun neverOpen() {
        val parsed = OpeningHours.parse("off")!!

        assertFalse(parsed.status(at(MONDAY, 12)).open)
        assertEquals("Closed", parsed.statusText(at(MONDAY, 12), SWISS).toString())
    }

    @Test
    fun whatTheAppDoesNotReadGivesNull() {
        for (
            hours in listOf(
                "",
                "Jan-Mar Mo-Fr 08:00-12:00",
                "Mo-Fr 08:00-18:00 \"by appointment\"",
                "sunrise-sunset",
                "Mo-Fr 08:00+",
                "Mo 25:00-26:00",
                "Mo 10:70-12:00",
                "Mo-Fr 12:00-14:00 off",
                "Mo[1] 10:00-12:00",
            )
        ) {
            assertNull(hours, OpeningHours.parse(hours))
        }
    }

    @Test
    fun theTimesAsThePhoneWritesThem() {
        val american = OpeningHours.parse(IKEA)!!.statusText(at(SATURDAY, 15), Locale.US)

        assertEquals("Open", american.headline)
        assertTrue(american.rest, american.rest.startsWith("Closes 8:00") && american.rest.endsWith("PM"))
        assertNotNull(OpeningHours.parse(IKEA)!!.week(Locale.US)[MONDAY].second)
    }
}
