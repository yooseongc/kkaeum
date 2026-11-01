package com.kkaeum.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReliabilityTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun durableProgressAndBundledSounds() {
        assertTrue(AlarmStorage.context(context).isDeviceProtectedStorage)
        val key = "test-storage"
        try {
            RingStore.add(context, SavedRing(key, Alarm(id = -999), 1, 123, true))
            RingStore.add(context, SavedRing(key, Alarm(id = -999), 1, 123, true))
            val mission = MathMission(3, 1)
            mission.submit(mission.problem.answer)
            RingStore.progress(context, key, mission)
            val saved = RingStore.read(context).single { it.key == key }
            val restored = MathMission(3, 1).also { it.restore(saved.completed, saved.left, saved.right) }
            assertEquals(1, restored.completed)
            assertEquals(mission.problem, restored.problem)
            AlarmSounds.builtins.forEach { sound ->
                context.contentResolver.openAssetFileDescriptor(AlarmSounds.resolve(context, sound.uri), "r")!!.use {
                    assertTrue(it.length > 1000)
                }
            }
        } finally { RingStore.remove(context, key) }
        assertFalse(RingStore.read(context).any { it.key == key })
    }
    @Test fun recentlyOverdueSurvivesBootClockSync() = runBlocking {
        val dao = AlarmDatabase.get(context).alarms()
        val scheduler = AlarmScheduler(context)
        val id = dao.put(Alarm(name = "[시각 동기화 검사]", days = 0)).toInt()
        val due = System.currentTimeMillis() - 5 * 60_000
        try {
            dao.putScheduled(Scheduled(id, due))
            scheduler.rebuild(recalculate = true)
            assertEquals(due, dao.scheduled(id)!!.due)
            assertTrue(dao.find(id)!!.enabled)
        } finally { scheduler.cancel(id); dao.delete(id) }
    }
    @Test fun overdueOneShotIsNotRescheduledTomorrow() = runBlocking {
        val dao = AlarmDatabase.get(context).alarms()
        val scheduler = AlarmScheduler(context)
        val id = dao.put(Alarm(name = "[만료 검사]", days = 0)).toInt()
        try {
            dao.putScheduled(Scheduled(id, System.currentTimeMillis() - 16 * 60_000))
            scheduler.rebuild()
            assertFalse(dao.find(id)!!.enabled)
            assertNull(dao.scheduled(id))
        } finally { scheduler.cancel(id); dao.delete(id) }
    }
}

/** Explicitly selected by adb for coordinated device experiments, never a normal test suite. */
class DeviceScenario {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun prepare(): Unit = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("scenario") == "true")
        val delay = args.getString("delaySeconds", "120")!!.toLong() * 1000
        val dao = AlarmDatabase.get(context).alarms()
        check(RingStore.read(context).isEmpty())
        check(dao.all().none { it.name.startsWith("[안정성 시험]") })
        val sound = if (args.getString("customSound") == "true") AlarmSounds.import(context, AlarmSounds.fallback(context)) else "kkaeum:pulse"
        val target = java.time.ZonedDateTime.now().plusSeconds(delay / 1000).withSecond(0).withNano(0).plusMinutes(1)
        val id = dao.put(Alarm(name = "[안정성 시험]", hour = target.hour, minute = target.minute, days = 0, count = 3, vibrate = false,
            soundUri = sound, soundName = "또렷한 비프")).toInt()
        AlarmScheduler(context).schedule(dao.find(id)!!)
        android.util.Log.i("KkaeumTest", "PREPARED id=$id due=${dao.scheduled(id)?.due}")
    }
    @Test fun cleanup(): Unit = runBlocking {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("scenario") == "true")
        val dao = AlarmDatabase.get(context).alarms()
        val ids = dao.all().filter { it.name.startsWith("[안정성 시험]") }.map { it.id }
        RingStore.read(context).filter { it.alarm.id in ids }.forEach { RingStore.remove(context, it.key) }
        context.stopService(android.content.Intent(context, RingService::class.java))
        ids.forEach { AlarmScheduler(context).cancel(it); dao.delete(it) }
        RingRecovery.arm(context)
        android.util.Log.i("KkaeumTest", "CLEANED remaining=${dao.all()}")
    }
}

