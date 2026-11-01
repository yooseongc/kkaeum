package com.kkaeum.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.room.withTransaction
import java.time.LocalDate

data class BackupData(val alarms: List<Alarm>, val rules: List<DateRule>, val pauses: List<PausePeriod>, val options: AppOptions)
object AlarmBackup {
    const val MAX_BYTES = 1024 * 1024
    suspend fun export(context: Context): String {
        val db=AlarmDatabase.get(context)
        val dao=db.alarms()
        val options=AppPreferences.read(context)
        return db.withTransaction {
            JSONObject().put("format","kkaeum").put("version",2)
                .put("alarms",JSONArray().also { a -> dao.all().forEach { a.put(JSONObject(AlarmCodec.encode(it))) } })
                .put("rules",JSONArray().also { a -> dao.rules().forEach { a.put(JSONObject().put("date",it.date).put("force",it.force)) } })
                .put("pauses",JSONArray().also { a -> dao.pauses().forEach { a.put(JSONObject().put("start",it.start).put("end",it.end)) } })
                .put("options",JSONObject().put("theme",options.theme).put("clock24",options.clock24).put("seconds",options.seconds)).toString(2)
        }
    }
    fun parse(text: String): BackupData {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "백업 파일이 너무 큽니다" }
        val root=JSONObject(text)
        require(root.getString("format")=="kkaeum" && root.getInt("version") in 1..2) { "지원하지 않는 백업 형식입니다" }
        val alarms=root.getJSONArray("alarms"); val rules=root.getJSONArray("rules"); val pauses=root.getJSONArray("pauses")
        require(alarms.length()<=200 && rules.length()<=2000 && pauses.length()<=100) { "백업 항목이 너무 많습니다" }
        val rows=(0 until alarms.length()).map {
            val a=AlarmCodec.decode(alarms.getJSONObject(it).toString())
            require(a.hour in 0..23 && a.minute in 0..59 && a.days in 0..127 && a.digits in 1..2 && a.count in 1..10 &&
                a.quietSeconds in 10..60 && a.snoozeMinutes in listOf(1,3,5,10,15) && a.snoozeLimit in 0..5 &&
                a.missionType in MissionTypes.names && a.missionLevel in 1..3 && a.choiceKind in MissionTypes.choices && a.name.length<=40 && a.groupName.length<=24 && a.soundUri.length<=4096)
            if (a.skipDate.isNotEmpty()) require(LocalDate.parse(a.skipDate).toString()==a.skipDate)
            val sound=AlarmSounds.builtins.firstOrNull { it.uri==a.soundUri }
            a.copy(id=0,enabled=false,groupName=a.groupName.trim(),soundUri=sound?.uri ?: "",soundName=sound?.title ?: "기본 알람음")
        }
        val dates=(0 until rules.length()).map { val j=rules.getJSONObject(it); val d=j.getString("date")
            require(LocalDate.parse(d).toString()==d); DateRule(d,j.getBoolean("force")) }
        require(dates.map { it.date }.distinct().size==dates.size)
        val periods=(0 until pauses.length()).map { val j=pauses.getJSONObject(it)
            val period=PausePeriod(j.getString("start"),j.getString("end")); validatePause(period); period }
        require(periods.map { it.start }.distinct().size==periods.size)
        val o=root.getJSONObject("options"); val theme=o.getString("theme")
        require(theme in listOf("system","light","dark"))
        return BackupData(rows,dates,periods,AppOptions(theme,o.getBoolean("clock24"),o.getBoolean("seconds")))
    }
    suspend fun restore(context: Context, data: BackupData, db: AlarmDatabase = AlarmDatabase.get(context)): Boolean {
        val dao=db.alarms()
        db.withTransaction {
            data.alarms.forEach { dao.put(it.copy(id=0,enabled=false)) }
            val existing=dao.rules().map { it.date }.toSet()
            data.rules.filter { it.date !in existing }.forEach { dao.putRule(it) }
            val starts=dao.pauses().map { it.start }.toSet()
            data.pauses.filter { it.start !in starts }.forEach { dao.putPause(it) }
        }
        return runCatching { AppPreferences.appearance(context,data.options) }.isSuccess
    }
}
fun validatePause(period: PausePeriod) {
    val start=LocalDate.parse(period.start); val end=LocalDate.parse(period.end)
    require(start.toString()==period.start && end.toString()==period.end && !end.isBefore(start) &&
        java.time.temporal.ChronoUnit.DAYS.between(start,end)<=366 && !end.isAfter(LocalDate.now().plusYears(2))) { "기간은 시작일부터 최대 367일까지 선택해 주세요" }
}
