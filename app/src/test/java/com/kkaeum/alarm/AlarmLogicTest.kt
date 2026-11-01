package com.kkaeum.alarm
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import kotlin.random.Random

class AlarmLogicTest {
    @Test fun weekdayAlarmSkipsWeekend() {
        val now = ZonedDateTime.parse("2026-10-23T08:00:00+09:00[Asia/Seoul]")
        assertEquals("2026-10-26T07:00+09:00[Asia/Seoul]", NextAlarm.at(Alarm(), now).toString())
    }
    @Test fun oneShotAtCurrentMinuteMovesToTomorrow() {
        val now = ZonedDateTime.parse("2026-10-25T07:00:00+09:00[Asia/Seoul]")
        assertEquals(26, NextAlarm.at(Alarm(days = 0), now).dayOfMonth)
    }
    @Test fun weeklyAlarmAfterItsTimeMovesSevenDays() {
        val now = ZonedDateTime.parse("2026-10-26T07:00:01+09:00[Asia/Seoul]")
        assertEquals("2026-11-02", NextAlarm.at(Alarm(days = 1), now).toLocalDate().toString())
    }
    @Test fun wrongAnswerDoesNotAdvanceOrReplaceProblem() {
        val mission = MathMission(3, 1, Random(7))
        val problem = mission.problem
        assertFalse(mission.submit(-1)); assertEquals(problem, mission.problem); assertEquals(0, mission.completed)
    }
    @Test fun exactlyThreeCorrectAnswersFinish() {
        val mission = MathMission(3, 2, Random(7))
        repeat(3) {
            assertTrue(mission.problem.left in 10..99 && mission.problem.right in 10..99)
            assertFalse(mission.finished); assertTrue(mission.submit(mission.problem.answer))
        }
        assertTrue(mission.finished); assertFalse(mission.submit(mission.problem.answer))
    }
}
