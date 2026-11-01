package com.kkaeum.alarm

import android.content.Context
import android.os.UserManager
import org.json.JSONArray
import org.json.JSONObject

object AlarmStorage {
    @Synchronized fun context(context: Context): Context {
        val app = context.applicationContext
        val device = app.createDeviceProtectedStorageContext()
        val preferences = device.getSharedPreferences("storage", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("migrated", false)) {
            check(app.getSystemService(UserManager::class.java).isUserUnlocked) { "업데이트 후 앱을 한 번 열어 주세요." }
            val credential = app
            if (credential.getDatabasePath("kkaeum.db").exists()) {
                check(!device.getDatabasePath("kkaeum.db").exists()) { "알람 저장소가 중복되었습니다." }
                check(device.moveDatabaseFrom(credential, "kkaeum.db")) { "알람 설정 이전에 실패했습니다." }
            }
            check(preferences.edit().putBoolean("migrated", true).commit())
        }
        return device
    }
}

data class SavedRing(val key: String, val alarm: Alarm, val used: Int, val due: Long,
    val snooze: Boolean, val completed: Int = 0, val left: Int = 0, val right: Int = 0)

/** Synchronous commits make delivery and mission progress durable before the next action. */
object RingStore {
    private fun prefs(context: Context) = AlarmStorage.context(context).getSharedPreferences("active_rings", Context.MODE_PRIVATE)
    @Synchronized fun read(context: Context): List<SavedRing> {
        val array = JSONArray(prefs(context).getString("rings", "[]"))
        return (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            SavedRing(o.getString("key"), AlarmCodec.decode(o.getString("alarm")), o.getInt("used"),
                o.getLong("due"), o.getBoolean("snooze"), o.optInt("completed"), o.optInt("left"), o.optInt("right"))
        }
    }
    private fun write(context: Context, rows: List<SavedRing>) {
        val array = JSONArray()
        rows.forEach { r -> array.put(JSONObject().put("key", r.key).put("alarm", AlarmCodec.encode(r.alarm))
            .put("used", r.used).put("due", r.due).put("snooze", r.snooze).put("completed", r.completed)
            .put("left", r.left).put("right", r.right)) }
        check(prefs(context).edit().putString("rings", array.toString()).commit()) { "알람 진행 상태를 저장하지 못했습니다." }
    }
    @Synchronized fun add(context: Context, row: SavedRing) {
        val rows = read(context)
        if (rows.none { it.key == row.key }) write(context, rows + row)
    }
    @Synchronized fun progress(context: Context, key: String, m: MathMission) {
        write(context, read(context).map { if (it.key == key) it.copy(completed = m.completed,
            left = m.problem.left, right = m.problem.right) else it })
    }
    @Synchronized fun remove(context: Context, key: String) { write(context, read(context).filterNot { it.key == key }) }
}

