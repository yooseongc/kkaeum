package com.kkaeum.alarm

import android.app.*
import android.content.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

object AlarmMutations { val mutex = Mutex() }
class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val dao = AlarmDatabase.get(context).alarms()
    fun allowed() = manager.canScheduleExactAlarms()
    private fun operation(id: Int, snooze: Boolean, due: Long = 0): PendingIntent = PendingIntent.getBroadcast(context, id,
        Intent(context, AlarmReceiver::class.java).setAction(if (snooze) "kkaeum.SNOOZE" else "kkaeum.RING")
            .putExtra("id", id).putExtra("due", due).putExtra("snooze", snooze),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun scheduleAt(id: Int, due: Long, snooze: Boolean, trigger: Long = due) {
        check(allowed()) { "정확한 알람 권한을 허용해 주세요." }
        val show = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), operation(id, snooze, due))
    }
    suspend fun next(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()) = NextAlarm.calculate(alarm, now,
        dao.holidays().associate { it.date to it.name }, dao.rules().associate { it.date to it.force })
    suspend fun schedule(alarm: Alarm) {
        if (!alarm.enabled) { cancelRegular(alarm.id); return }
        val due = next(alarm).time.toInstant().toEpochMilli()
        dao.putScheduled(Scheduled(alarm.id, due))
        scheduleAt(alarm.id, due, false)
    }
    suspend fun snooze(row: Snooze) {
        dao.putSnooze(row); scheduleAt(row.alarmId, row.due, true)
    }
    suspend fun cancelRegular(id: Int) {
        manager.cancel(operation(id, false)); dao.clearScheduled(id)
        // Cancel v0.1 pending intents as well, without creating one.
        PendingIntent.getBroadcast(context, id, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { manager.cancel(it); it.cancel() }
    }
    suspend fun cancelSnooze(id: Int) { manager.cancel(operation(id, true)); dao.clearSnooze(id) }
    suspend fun cancel(id: Int) { cancelRegular(id); cancelSnooze(id) }
    suspend fun consume(row: SavedRing) {
        val alarm = dao.find(row.alarm.id) ?: return
        if (row.snooze) {
            if (dao.snooze(alarm.id)?.due == row.due) dao.clearSnooze(alarm.id)
        } else if (dao.scheduled(alarm.id)?.due == row.due) {
            dao.clearScheduled(alarm.id)
            val updated = alarm.copy(enabled = alarm.days != 0,
                skipDate = alarm.skipDate.takeIf { it > java.time.LocalDate.now().toString() }.orEmpty())
            dao.put(updated); schedule(updated)
        }
    }
    suspend fun rebuild(recalculate: Boolean = false) {
        if (!allowed()) return
        RingStore.read(context).forEach { consume(it) }
        val now = System.currentTimeMillis()
        dao.all().forEach { alarm ->
            val pending = dao.scheduled(alarm.id)
            when {
                !alarm.enabled -> cancelRegular(alarm.id)
                pending != null && pending.due <= now -> {
                    when {
                        now - pending.due <= 15 * 60_000 -> scheduleAt(alarm.id, pending.due, false, now + 2000)
                        alarm.days == 0 -> { dao.put(alarm.copy(enabled = false)); cancelRegular(alarm.id) }
                        else -> schedule(alarm)
                    }
                }
                recalculate || pending == null -> schedule(alarm)
                else -> scheduleAt(alarm.id, pending.due, false)
            }
        }
        dao.snoozes().forEach { row ->
            if (dao.find(row.alarmId) == null || now - row.due > 15 * 60_000) cancelSnooze(row.alarmId)
            else scheduleAt(row.alarmId, row.due, true, maxOf(row.due, now + 2000))
        }
        RingRecovery.arm(context, 3000)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("Kkaeum", "Receive ${intent.action} unlocked=${context.getSystemService(android.os.UserManager::class.java).isUserUnlocked}")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { AlarmMutations.mutex.withLock {
                val dao = AlarmDatabase.get(context).alarms()
                val id = intent.getIntExtra("id", -1)
                val alarm = dao.find(id) ?: return@withLock
                val isSnooze = intent.getBooleanExtra("snooze", false)
                val expected = intent.getLongExtra("due", 0)
                val snooze = if (isSnooze) dao.snooze(id) else null
                val due = if (isSnooze) snooze?.due else dao.scheduled(id)?.due
                if (due == null || due != expected || (!isSnooze && !alarm.enabled)) return@withLock
                if (System.currentTimeMillis() + 1000 < due) return@withLock
                val saved = SavedRing("$id:$due:$isSnooze", alarm, snooze?.used ?: 0, due, isSnooze)
                RingStore.add(context, saved)
                RingRecovery.arm(context)
                AlarmScheduler(context).consume(saved)
                context.startForegroundService(Intent(context, RingService::class.java))
            } } catch (e: Exception) { Log.e("Kkaeum", "Alarm delivery failed", e) }
            finally { pending.finish() }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        Log.i("Kkaeum", "Receive ${intent.action} unlocked=${context.getSystemService(android.os.UserManager::class.java).isUserUnlocked}")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                HolidayRepository(context).seed()
                AlarmMutations.mutex.withLock { AlarmScheduler(context).rebuild(intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) }
            } catch (e: Exception) { Log.e("Kkaeum", "Reschedule failed", e) }
            finally { pending.finish() }
        }
    }
}

/** A user alarm may resume a foreground service from an exact-alarm receiver, including after boot. */
object RingRecovery {
    fun arm(context: Context, delay: Long = 30_000) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(context, 0, Intent(context, RecoveryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (RingStore.read(context).isEmpty()) { manager.cancel(operation); return }
        if (!manager.canScheduleExactAlarms()) return
        val show = PendingIntent.getActivity(context, 0, Intent(context, MissionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delay, show), operation)
    }
}
class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (RingStore.read(context).isNotEmpty()) {
                RingRecovery.arm(context)
                context.startForegroundService(Intent(context, RingService::class.java))
            }
        } catch (e: Exception) { Log.e("Kkaeum", "Active alarm recovery failed", e) }
    }
}
