package com.cristianku.nav2pilot.ui

import com.cristianku.nav2pilot.testing.FakeHttpClient
import org.junit.Assert.*
import org.junit.Test

class TesterFeedbackTest {
    @Test fun invitationCountsDistinctDaysAndSurvivesSnoozeAndOptOut() {
        var state = FeedbackVisits()
        for (day in listOf(100L, 100L, 100L, 106L)) {
            val (next, ask) = state.visit(day)
            assertFalse(ask)
            state = next
        }
        val (asked, due) = state.visit(108)
        assertTrue(due)
        assertFalse(asked.visit(108).second)
        assertFalse(asked.visit(167).second)
        assertTrue(asked.visit(168).second)
        assertFalse(asked.copy(stopped = true).visit(1000).second)
    }
    @Test fun onlyCreatedConfirmsTheReportWithoutSendingNavigationData() {
        val http = FakeHttpClient()
        val report = TesterInbox.report("  Parking card is useful  ")
        assertTrue(report.endsWith("\n[message]\nParking card is useful\n"))
        assertFalse(TesterInbox.send(http, "", report))
        assertTrue(http.requests.isEmpty())
        for (status in listOf(200, 403, 429, 500, 201)) {
            http.respond(status, "{}")
            assertEquals(status == 201, TesterInbox.send(http, "test-key", report))
            val req = http.requests.last()
            assertEquals("nav2pilot", req.headers["X-App"])
            assertEquals(report, req.body)
            assertFalse(req.followRedirects)
        }
    }
}
