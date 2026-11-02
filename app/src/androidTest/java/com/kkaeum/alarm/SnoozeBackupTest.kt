package com.kkaeum.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SnoozeBackupTest {
    @Test fun extendedLimitsSurviveCodecAndBackup() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val root = JSONObject(AlarmBackup.export(context))
        for (limit in listOf(-1, 0, 3, 5, 10)) {
            val alarm = Alarm(snoozeLimit = limit)
            val encoded = AlarmCodec.encode(alarm)
            assertEquals(limit, AlarmCodec.decode(encoded).snoozeLimit)
            root.put("alarms", JSONArray().put(JSONObject(encoded)))
            assertEquals(limit, AlarmBackup.parse(root.toString()).alarms.single().snoozeLimit)
        }
        for (limit in listOf(-2, 11)) {
            root.put("alarms", JSONArray().put(JSONObject(AlarmCodec.encode(Alarm(snoozeLimit = limit)))))
            assertTrue(runCatching { AlarmBackup.parse(root.toString()) }.isFailure)
        }
    }
}
