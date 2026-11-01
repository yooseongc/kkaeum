package com.kkaeum.alarm
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test
class PauseLogicTest {
    private val now=ZonedDateTime.parse("2026-12-24T08:00:00+09:00[Asia/Seoul]")
    @Test fun intervalIsInclusiveAndScheduleResumes() {
        val a=Alarm(days=127)
        val result=NextAlarm.calculate(a,now,pauses=listOf(PausePeriod("2026-12-25","2026-12-31")))
        assertEquals("2027-01-01",result.time.toLocalDate().toString()); assertEquals(7,result.skipped.size)
        assertEquals("기간 건너뛰기",result.skipped.first().reason)
    }
    @Test fun intervalOverridesForcedHoliday() {
        val result=NextAlarm.calculate(Alarm(),now,rules=mapOf("2026-12-25" to true),pauses=listOf(PausePeriod("2026-12-25","2026-12-25")))
        assertEquals("2026-12-28",result.time.toLocalDate().toString())
    }
    @Test fun overlappingIntervalsAndOneTimeAlarm() {
        val result=NextAlarm.calculate(Alarm(days=0),now,pauses=listOf(PausePeriod("2026-12-25","2026-12-27"),PausePeriod("2026-12-27","2026-12-29")))
        assertEquals("2026-12-30",result.time.toLocalDate().toString())
    }
    @Test fun expiredIntervalDoesNotAffectNextAlarm() {
        assertEquals("2026-12-25",NextAlarm.calculate(Alarm(),now,pauses=listOf(PausePeriod("2026-12-01","2026-12-24"))).time.toLocalDate().toString())
    }
    @Test fun groupDoesNotChangeAlarmTiming() {
        assertEquals(NextAlarm.calculate(Alarm(),now),NextAlarm.calculate(Alarm(groupName="출근"),now))
    }
}
