package com.kkaeum.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class MissionDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun legacyBackupAndEveryNewMissionRoundTrip() = runBlocking {
        val root = JSONObject(AlarmBackup.export(context))
        val legacy = JSONObject(AlarmCodec.encode(Alarm(digits = 2, count = 5)))
        listOf("missionType", "missionLevel", "choiceKind").forEach { legacy.remove(it) }
        root.put("version", 1).put("alarms", JSONArray().put(legacy))
        val old = AlarmBackup.parse(root.toString()).alarms.single()
        assertEquals("math", old.missionType); assertEquals(2, old.digits); assertEquals(5, old.count)
        val oldRing = SavedRing("legacy", old, 0, 123, false, completed = 2, left = 12, right = 34)
        val resumed = MissionFactory.create(old, oldRing.progressSnapshot()) as SumSession
        assertEquals(2, resumed.completed); assertEquals(46, resumed.math.problem.answer)
        for (type in MissionTypes.names.keys) {
            val alarm = Alarm(missionType = type, missionLevel = 3, choiceKind = "direction", enabled = true)
            root.put("version", 2).put("alarms", JSONArray().put(JSONObject(AlarmCodec.encode(alarm))))
            val restored = AlarmBackup.parse(root.toString()).alarms.single()
            assertEquals(type, restored.missionType); assertEquals(3, restored.missionLevel)
            assertEquals("direction", restored.choiceKind); assertFalse(restored.enabled)
        }
        root.getJSONArray("alarms").getJSONObject(0).put("missionType", "unknown")
        assertTrue(runCatching { AlarmBackup.parse(root.toString()) }.isFailure)
    }

    @Test fun durableProgressSurvivesServiceRecreationAndCompletes() = runBlocking {
        assumeTrue(RingService.state.value == null && RingStore.read(context).isEmpty())
        val prefs = AlarmStorage.context(context).getSharedPreferences("alarm_history", Context.MODE_PRIVATE)
        val history = prefs.getString("events", "[]")
        val key = "mission-test-${System.nanoTime()}"
        val alarm = Alarm(id = -712, name = "[미션 검사]", count = 1, vibrate = false, missionType = "order")
        val session = MissionFactory.create(alarm) as TouchMission
        session.tap(session.solution.first())
        RingStore.add(context, SavedRing(key, alarm, 0, System.currentTimeMillis(), false, missionState = session.snapshot()))
        val intent = Intent(context, RingService::class.java)
        try {
            context.startForegroundService(intent)
            withTimeout(10000) { while (RingService.state.value?.key != key) delay(50) }
            assertEquals(session.snapshot(), RingService.state.value!!.missionState)
            withContext(Dispatchers.Main) { RingService.instance!!.select(session.solution[1]) }
            val saved = RingStore.read(context).single().missionState
            assertEquals(2, (MissionFactory.create(alarm, saved) as TouchMission).step)
            context.stopService(intent)
            withTimeout(5000) { while (RingService.instance != null) delay(50) }
            context.startForegroundService(intent)
            withTimeout(10000) { while (RingService.state.value?.key != key) delay(50) }
            assertEquals(saved, RingService.state.value!!.missionState)
            withContext(Dispatchers.Main) {
                val restored = MissionFactory.create(alarm, saved) as TouchMission
                restored.solution.drop(restored.step).forEach { RingService.instance!!.select(it) }
            }
            withTimeout(5000) { while (RingService.state.value != null) delay(50) }
            assertTrue(RingStore.read(context).isEmpty())
        } finally {
            RingStore.remove(context, key); context.stopService(intent); RingRecovery.arm(context)
            prefs.edit().putString("events", history).commit(); AlarmHistory.revision.value++
        }
    }
}
