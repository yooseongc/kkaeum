package com.kkaeum.alarm
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class UpcomingAlarmTest {
    private val now = ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, ZoneId.of("Asia/Seoul"))
    @Test fun disabledAlarmsAndExpiredSnoozesAreExcluded() {
        assertNull(upcomingAlarm(listOf(Alarm(id=1, enabled=false)), listOf(Snooze(1, now.minusMinutes(1).toInstant().toEpochMilli(), 1)), now, emptyMap(), emptyMap()))
    }
    @Test fun pendingSnoozeIncludesDisabledOneTimeAlarm() {
        val alarm = Alarm(id=1, enabled=false)
        val due = now.plusMinutes(5)
        val result = upcomingAlarm(listOf(alarm), listOf(Snooze(1, due.toInstant().toEpochMilli(),1)),now,emptyMap(),emptyMap())!!
        assertTrue(result.snooze); assertEquals(due,result.time)
    }
    @Test fun earliestRegularAlarmWinsOverLaterSnooze() {
        val a=Alarm(id=1,hour=23,minute=10,days=127)
        val result=upcomingAlarm(listOf(a),listOf(Snooze(1,now.plusMinutes(20).toInstant().toEpochMilli(),1)),now,emptyMap(),emptyMap())!!
        assertFalse(result.snooze); assertEquals(now.plusMinutes(10),result.time)
    }
    @Test fun holidaySkipRespectsIndividualOption() {
        val a=Alarm(id=1,hour=7,days=127,skipHolidays=true)
        val b=a.copy(id=2,skipHolidays=false)
        val result=upcomingAlarm(listOf(a,b),emptyList(),now,mapOf("2027-01-01" to "신정"),emptyMap())!!
        assertEquals(2,result.alarm.id); assertEquals(LocalDate.of(2027,1,1),result.time.toLocalDate())
    }
    @Test fun checksOnlyReportProblems() {
        assertTrue(AlarmCheck(true,true,true,5,15).issues.isEmpty())
        assertEquals(listOf("알람 음량이 0입니다"),AlarmCheck(true,true,true,0,15).issues)
        assertEquals(4,AlarmCheck(false,false,false,0,15).issues.size)
    }
    @Test fun tomorrowCrossesYearBoundary() {
        assertEquals("내일 1월 1일 금요일",alarmDateLabel(now.plusHours(2),now))
    }
    @Test fun remainingTimeHandlesSecondsAndExactHour() {
        assertEquals("곧 울림",remainingLabel(now.plusSeconds(59),now))
        assertEquals("2분 후",remainingLabel(now.plusSeconds(61),now))
        assertEquals("1시간 후",remainingLabel(now.plusHours(1),now))
    }
}
