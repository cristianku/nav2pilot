package com.cristianku.nav2pilot.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackMailTest {

    @Test
    fun subjectNamesTheVersion() {
        assertEquals("Nav2Pilot feedback – 1.0.32 (Android)", FeedbackMail.subject("1.0.32"))
        assertEquals("Nav2Pilot feedback", FeedbackMail.subject(""))
    }

    @Test
    fun bodyLeavesRoomToWriteAboveTheVersions() {
        assertEquals(
            "\n\n\n--\nNav2Pilot 1.0.32, Android 15, Google Pixel 8",
            FeedbackMail.body("1.0.32", "15", "Google Pixel 8"),
        )
        assertEquals("\n\n\n--\nAndroid 15", FeedbackMail.body("", "15", ""))
    }

    @Test
    fun phoneNameDoesNotRepeatTheMaker() {
        assertEquals("Google Pixel 8", FeedbackMail.phoneName("Google", "Pixel 8"))
        assertEquals("samsung SM-G991B".replace("samsung", "Samsung"), FeedbackMail.phoneName("samsung", "SM-G991B"))
        assertEquals("OnePlus 12", FeedbackMail.phoneName("OnePlus", "OnePlus 12"))
        assertEquals("Pixel 8", FeedbackMail.phoneName("", "Pixel 8"))
    }

    @Test
    fun mailtoEncodesSpacesAndLineBreaks() {
        assertEquals(
            "mailto:admin@14bodhi.com?subject=Nav2Pilot%20feedback&body=%0A--%0AAndroid%2015%2C%20Pixel%208",
            FeedbackMail.mailto("Nav2Pilot feedback", "\n--\nAndroid 15, Pixel 8"),
        )
    }
}
