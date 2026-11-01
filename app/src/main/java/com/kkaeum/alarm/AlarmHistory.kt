package com.kkaeum.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow

data class AlarmEvent(val key: String, val name: String, val due: Long, val at: Long, val action: String)
object AlarmHistory {
    val revision = MutableStateFlow(0)
    private fun prefs(context: Context) = AlarmStorage.context(context).getSharedPreferences("alarm_history", Context.MODE_PRIVATE)
    @Synchronized fun read(context: Context): List<AlarmEvent> = runCatching {
        val a = JSONArray(prefs(context).getString("events", "[]"))
        (0 until a.length()).map { val j=a.getJSONObject(it)
            AlarmEvent(j.getString("key"), j.getString("name"), j.getLong("due"), j.getLong("at"), j.getString("action")) }.filter { it.at >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 }
    }.getOrDefault(emptyList())
    @Synchronized fun record(context: Context, key: String, name: String, due: Long, action: String) {
        // Diagnostics must never prevent the alarm itself from ringing.
        runCatching {
            val rows = read(context)
            if (rows.any { it.key == key }) return
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val updated = (rows.filter { it.at >= cutoff } + AlarmEvent(key,name,due,System.currentTimeMillis(),action)).takeLast(500)
            val a=JSONArray()
            updated.forEach { a.put(JSONObject().put("key",it.key).put("name",it.name).put("due",it.due).put("at",it.at).put("action",it.action)) }
            check(prefs(context).edit().putString("events",a.toString()).commit())
            revision.value++
        }.onFailure { android.util.Log.w("Kkaeum", "History write failed", it) }
    }
    @Synchronized fun clear(context: Context) { check(prefs(context).edit().remove("events").commit()); revision.value++ }
}
