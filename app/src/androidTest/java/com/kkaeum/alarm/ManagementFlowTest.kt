package com.kkaeum.alarm
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
class ManagementFlowTest {
    @Test fun groupSwitchAndPauseRecalculatePersistedSchedules() = runBlocking {
        val app: Application=ApplicationProvider.getApplicationContext()
        val dao=AlarmDatabase.get(app).alarms(); val scheduler=AlarmScheduler(app)
        assumeTrue(scheduler.allowed()); assumeTrue(dao.all().none { it.enabled })
        val group="[검사]"+System.nanoTime()
        val first=dao.put(Alarm(name="[그룹 검사1]",enabled=false,groupName=group,days=127)).toInt()
        val second=dao.put(Alarm(name="[그룹 검사2]",enabled=false,groupName=group,days=127)).toInt()
        val start=LocalDate.now().toString(); val existing=dao.pauses().find { it.start==start }
        lateinit var vm: AlarmViewModel
        val instrument=InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync { vm=AlarmViewModel(app) }
        try {
            withTimeout(10000) { while(!vm.ready.value) delay(50) }
            instrument.runOnMainSync { vm.group(group,true) }
            withTimeout(10000) { while(dao.scheduled(first)==null || dao.scheduled(second)==null) delay(50) }
            assertTrue(dao.find(first)!!.enabled); assertTrue(dao.find(second)!!.enabled)
            val period=PausePeriod(start,LocalDate.now().plusDays(2).toString())
            instrument.runOnMainSync { vm.pause(period) }
            withTimeout(10000) { while(dao.pauses().none { it==period } || scheduler.next(dao.find(first)!!).time.toLocalDate()<=LocalDate.now().plusDays(2)) delay(50) }
            withTimeout(10000) { while(dao.scheduled(first)!!.due!=scheduler.next(dao.find(first)!!).time.toInstant().toEpochMilli()) delay(50) }
            assertTrue(runCatching { scheduler.snooze(Snooze(first,System.currentTimeMillis()+60000,1)) }.isFailure)
            assertNull(dao.snooze(first))
            instrument.runOnMainSync { vm.group(group,false) }
            withTimeout(10000) { while(dao.find(first)!!.enabled || dao.scheduled(first)!=null || dao.scheduled(second)!=null) delay(50) }
        } finally {
            if(existing==null) dao.deletePause(start) else dao.putPause(existing)
            scheduler.cancel(first); scheduler.cancel(second); dao.delete(first); dao.delete(second)
            scheduler.rebuild(recalculate=true)
        }
    }
}
