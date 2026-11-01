package com.kkaeum.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDeliveryTest {
    private fun currentAnswer(): Int {
        val state = RingService.state.value!!
        return (MissionFactory.create(state.alarm, state.missionState) as SumSession).math.problem.answer
    }
    @Test fun exactDeliverySnoozeAndMissionCompletion() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val scheduler = AlarmScheduler(context)
        assumeTrue("Requires exact-alarm permission", scheduler.allowed())
        assumeTrue("Do not interrupt an existing alarm", RingService.state.value == null)
        val historyPrefs=AlarmStorage.context(context).getSharedPreferences("alarm_history",Context.MODE_PRIVATE)
        val priorHistory=historyPrefs.getString("events","[]")
        val dao = AlarmDatabase.get(context).alarms()
        val id = dao.put(Alarm(name = "[자동 검사]", enabled = false, count = 2, snoozeMinutes = 1,
            snoozeLimit = 1, vibrate = false)).toInt()
        try {
            AlarmMutations.mutex.withLock { scheduler.snooze(Snooze(id, System.currentTimeMillis() + 3000, 0)) }
            withTimeout(20000) { while (RingService.state.value?.alarm?.id != id) delay(100) }
            withContext(Dispatchers.Main) { RingService.instance!!.interact(); RingService.instance!!.snooze() }
            withTimeout(10000) { while (dao.snooze(id)?.used != 1 || RingService.state.value != null) delay(100) }
            val pending = dao.snooze(id)!!
            assertTrue(pending.due > System.currentTimeMillis() + 40_000)
            AlarmMutations.mutex.withLock { scheduler.snooze(pending.copy(due = System.currentTimeMillis() + 3000)) }
            withTimeout(20000) { while (RingService.state.value?.alarm?.id != id) delay(100) }
            assertEquals(1, RingService.state.value!!.used)
            withContext(Dispatchers.Main) {
                val service = RingService.instance!!
                service.submit(-1)
                assertEquals(0, RingService.state.value!!.completed)
                assertTrue(RingService.state.value!!.quiet)
                service.snooze() // Limit reached: must keep the alarm active.
                assertNotNull(RingService.state.value)
                repeat(2) { service.submit(currentAnswer()) }
            }
            withTimeout(5000) { while (RingService.state.value != null) delay(100) }
            assertNull(dao.snooze(id))
            val events=AlarmHistory.read(context).filter { it.key.startsWith("$id:") }
            assertTrue(events.any { it.action=="알람 수신" })
            assertTrue(events.any { it.action=="울림 시작" })
            assertTrue(events.any { it.action=="미션 완료" })
            assertTrue(events.any { it.action.contains("뒤 다시 울림") })
        } finally {
            historyPrefs.edit().putString("events",priorHistory).commit(); AlarmHistory.revision.value++
            if (RingService.state.value?.alarm?.id == id) withContext(Dispatchers.Main) {
                while (RingService.state.value?.alarm?.id == id) RingService.instance?.submit(currentAnswer())
            }
            AlarmMutations.mutex.withLock { scheduler.cancel(id); dao.delete(id) }
        }
    }
}
