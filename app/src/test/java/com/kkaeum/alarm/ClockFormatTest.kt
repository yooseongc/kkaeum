package com.kkaeum.alarm

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ClockFormatTest {
    @Test fun midnightAndNoonUseCorrectPeriod() {
        val options = AppOptions(clock24 = false)
        assertEquals("오전 12:00", displayTime(LocalTime.MIDNIGHT, options))
        assertEquals("오후 12:00", displayTime(LocalTime.NOON, options))
    }
    @Test fun secondsFollowClockPreferenceButNeverAppearInAlarmTime() {
        val time = LocalTime.of(21, 6, 7)
        assertEquals("21:06:07", displayTime(time, AppOptions(), seconds = true))
        assertEquals("21:06", displayTime(time, AppOptions(seconds = false), seconds = true))
        assertEquals("21:06", displayTime(time, AppOptions()))
    }
}
