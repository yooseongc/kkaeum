package com.kkaeum.alarm
import org.json.JSONObject

object AlarmCodec {
    fun encode(a: Alarm): String = JSONObject().apply {
        put("groupName", a.groupName); put("id", a.id); put("hour", a.hour); put("minute", a.minute); put("days", a.days)
        put("enabled", a.enabled); put("digits", a.digits); put("count", a.count)
        put("quietSeconds", a.quietSeconds); put("vibrate", a.vibrate); put("name", a.name)
        put("soundUri", a.soundUri); put("soundName", a.soundName); put("skipHolidays", a.skipHolidays)
        put("skipDate", a.skipDate); put("snoozeMinutes", a.snoozeMinutes); put("snoozeLimit", a.snoozeLimit)
    }.toString()
    fun decode(text: String): Alarm = JSONObject(text).let { j -> Alarm(
        groupName=j.optString("groupName", ""), id=j.getInt("id"), hour=j.getInt("hour"), minute=j.getInt("minute"), days=j.getInt("days"),
        enabled=j.getBoolean("enabled"), digits=j.getInt("digits"), count=j.getInt("count"),
        quietSeconds=j.getInt("quietSeconds"), vibrate=j.getBoolean("vibrate"), name=j.getString("name"),
        soundUri=j.getString("soundUri"), soundName=j.getString("soundName"), skipHolidays=j.getBoolean("skipHolidays"),
        skipDate=j.getString("skipDate"), snoozeMinutes=j.getInt("snoozeMinutes"), snoozeLimit=j.getInt("snoozeLimit")) }
}
