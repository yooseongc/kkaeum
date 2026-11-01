package com.kkaeum.alarm

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class UpcomingAlarm(val alarm: Alarm, val time: ZonedDateTime, val snooze: Boolean)
fun upcomingAlarm(alarms: List<Alarm>, snoozes: List<Snooze>, now: ZonedDateTime,
    holidays: Map<String, String>, rules: Map<String, Boolean>): UpcomingAlarm? {
    val regular = alarms.filter { it.enabled }.map {
        UpcomingAlarm(it, NextAlarm.calculate(it, now, holidays, rules).time, false)
    }
    val pending = snoozes.mapNotNull { s ->
        val alarm = alarms.find { it.id == s.alarmId } ?: return@mapNotNull null
        val time = Instant.ofEpochMilli(s.due).atZone(now.zone)
        if (time.isAfter(now)) UpcomingAlarm(alarm, time, true) else null
    }
    return (regular + pending).minWithOrNull(compareBy<UpcomingAlarm> { it.time }.thenBy { !it.snooze }.thenBy { it.alarm.id })
}
fun alarmDateLabel(time: ZonedDateTime, now: ZonedDateTime): String {
    val date = time.withZoneSameInstant(now.zone).toLocalDate()
    val prefix = when (date) { now.toLocalDate() -> "오늘 "; now.toLocalDate().plusDays(1) -> "내일 "; else -> "" }
    return prefix + date.format(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN))
}
fun remainingLabel(time: ZonedDateTime, now: ZonedDateTime): String {
    val seconds = Duration.between(now, time).seconds
    if (seconds < 60) return "곧 울림"
    val minutes = (seconds + 59) / 60
    return if (minutes < 60) "${minutes}분 후" else if (minutes % 60 == 0L) "${minutes / 60}시간 후"
        else "${minutes / 60}시간 ${minutes % 60}분 후"
}
