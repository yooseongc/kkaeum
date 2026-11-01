package com.kkaeum.alarm

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class CalendarLogicTest {
    private val friday = ZonedDateTime.parse("2026-12-24T08:00:00+09:00[Asia/Seoul]")
    private val holidays = mapOf("2026-12-25" to "기독탄신일")
    @Test fun holidayIsSkippedWithoutChangingWeekdays() {
        val result = NextAlarm.calculate(Alarm(skipHolidays = true), friday, holidays)
        assertEquals("2026-12-28", result.time.toLocalDate().toString())
        assertEquals("기독탄신일", result.skipped.single().reason)
    }
    @Test fun holidaySwitchOffStillRings() {
        assertEquals("2026-12-25", NextAlarm.calculate(Alarm(), friday, holidays).time.toLocalDate().toString())
    }
    @Test fun forcedDateOverridesHoliday() {
        assertEquals("2026-12-25", NextAlarm.calculate(Alarm(skipHolidays = true), friday, holidays,
            mapOf("2026-12-25" to true)).time.toLocalDate().toString())
    }
    @Test fun forcedWeekendAddsAnOccurrence() {
        val now = ZonedDateTime.parse("2026-10-30T08:00:00+09:00[Asia/Seoul]")
        assertEquals("2026-10-31", NextAlarm.calculate(Alarm(), now, rules = mapOf("2026-10-31" to true)).time.toLocalDate().toString())
    }
    @Test fun skipOnceWinsOverForcedDay() {
        val result = NextAlarm.calculate(Alarm(skipDate = "2026-12-25"), friday, holidays, mapOf("2026-12-25" to true))
        assertEquals("2026-12-28", result.time.toLocalDate().toString())
        assertEquals("이번만 건너뛰기", result.skipped.single().reason)
    }
    @Test fun privateVacationWorksWithoutHolidaySwitch() {
        assertEquals("2026-12-28", NextAlarm.calculate(Alarm(), friday, rules = mapOf("2026-12-25" to false)).time.toLocalDate().toString())
    }
    @Test fun longVacationCanSkipMoreThanOneWeek() {
        val rules = (25..31).associate { "2026-12-$it" to false }
        assertEquals("2027-01-01", NextAlarm.calculate(Alarm(), friday, rules = rules).time.toLocalDate().toString())
    }
    @Test fun oneShotSkipsHolidayToFollowingDay() {
        assertEquals("2026-12-26", NextAlarm.calculate(Alarm(days = 0, skipHolidays = true), friday, holidays).time.toLocalDate().toString())
    }
    @Test fun expiredSkipDoesNotAffectFutureDates() {
        assertEquals("2026-12-25", NextAlarm.calculate(Alarm(skipDate = "2025-12-25"), friday).time.toLocalDate().toString())
    }
}
