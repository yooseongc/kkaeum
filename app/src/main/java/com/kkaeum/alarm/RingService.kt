package com.kkaeum.alarm

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class RingState(val key: String, val alarm: Alarm, val problem: SumProblem, val completed: Int,
    val total: Int, val preview: Boolean, val used: Int, val quiet: Boolean = false, val message: String = "")
private data class RingRequest(val alarm: Alarm, val preview: Boolean, val used: Int)

class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var request: RingRequest? = null
    private var mission: MathMission? = null
    private var key = ""
    
    private var timeout: Job? = null
    private var previewTimeout: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val audio by lazy { AlarmAudio(this) }
    private val vibrator by lazy { getSystemService(VibratorManager::class.java).defaultVibrator }
    private var snoozing = false

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate(); instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "울리는 알람", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (request == null) startForeground(1, notification())
        val saved = RingStore.read(this).firstOrNull()
        if (saved != null) {
            if (request == null || request?.preview == true) {
                timeout?.cancel(); previewTimeout?.cancel(); audio.stop(); vibrator.cancel()
                begin(RingRequest(saved.alarm, false, saved.used), saved)
            }
            RingRecovery.arm(this)
            return START_STICKY
        }
        val config = intent?.getStringExtra("config")
        if (config != null && intent.getBooleanExtra("preview", false) && request == null) {
            runCatching { AlarmCodec.decode(config) }.getOrNull()?.let { begin(RingRequest(it, true, 0)) }
        }
        if (request == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("깨움 · 알람").setContentText("수학 미션을 완료하면 알람이 꺼집니다")
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true).setContentIntent(open).setFullScreenIntent(open, true).build()
    }
    private fun begin(next: RingRequest, saved: SavedRing? = null) {
        request = next; key = saved?.key ?: UUID.randomUUID().toString()
        mission = MathMission(next.alarm.count, next.alarm.digits).also { m ->
            if (saved != null && saved.left != 0) m.restore(saved.completed, saved.left, saved.right)
        }
        if (!next.preview) RingStore.progress(this, key, mission!!)
        if (wakeLock?.isHeld != true) wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kkaeum:mission").apply { acquire(60 * 60 * 1000L) }
        android.util.Log.i("KkaeumRing", "BEGIN key=$key completed=${mission?.completed} unlocked=${getSystemService(android.os.UserManager::class.java).isUserUnlocked}")
        ring()
        if (next.preview) previewTimeout = scope.launch { delay(90_000); finishCurrent() }
    }
    private fun publish(quiet: Boolean, message: String = "") {
        val r = request ?: return; val m = mission ?: return
        if (!r.preview && !m.finished) RingStore.progress(this, key, m)
        mutableState.value = RingState(key, r.alarm, m.problem, m.completed, m.total, r.preview, r.used, quiet, message)
    }
    private fun ring() {
        timeout?.cancel(); publish(false)
        audio.play(request?.alarm?.soundUri.orEmpty()) { publish(false, "선택한 소리를 재생할 수 없어 기본음을 사용해요.") }
        if (request?.alarm?.vibrate == true) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
            if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
            else @Suppress("DEPRECATION") vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
    fun interact() {
        if (mission == null) return
        audio.stop(); vibrator.cancel(); timeout?.cancel(); publish(true)
        timeout = scope.launch { delay((request?.alarm?.quietSeconds ?: 15) * 1000L); ring() }
    }
    fun submit(value: Int?) {
        val m = mission ?: return
        interact()
        if (value == null || !m.submit(value)) { publish(true, "다시 계산해 보세요"); return }
        if (m.finished) finishCurrent() else publish(true)
    }
    fun stopPreview() { if (request?.preview == true) finishCurrent() }
    fun snooze() {
        val r = request ?: return
        if (r.preview || r.used >= r.alarm.snoozeLimit || snoozing) return
        snoozing = true
        scope.launch {
            try {
                AlarmMutations.mutex.withLock {
                    check(AlarmDatabase.get(this@RingService).alarms().find(r.alarm.id) != null) { "삭제된 알람입니다." }
                    AlarmScheduler(this@RingService).snooze(Snooze(r.alarm.id,
                        System.currentTimeMillis() + r.alarm.snoozeMinutes * 60_000L, r.used + 1))
                }
                finishCurrent()
            } catch (e: Exception) { publish(mutableState.value?.quiet ?: false, "다시 울림을 예약하지 못했어요. 알람 권한을 확인해 주세요.") }
            finally { snoozing = false }
        }
    }
    private fun finishCurrent() {
        timeout?.cancel(); previewTimeout?.cancel(); audio.stop(); vibrator.cancel()
        if (request?.preview == false) RingStore.remove(this, key)
        request = null; mission = null
        RingRecovery.arm(this)
        val next = RingStore.read(this).firstOrNull()
        if (next != null) begin(RingRequest(next.alarm, false, next.used), next) else {
            mutableState.value = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    override fun onTaskRemoved(rootIntent: Intent?) { RingRecovery.arm(this, 3000) }
    override fun onDestroy() {
        scope.cancel(); audio.stop(); vibrator.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        instance = null; mutableState.value = null; super.onDestroy()
    }
    companion object {
        const val CHANNEL = "ringing"
        private val mutableState = MutableStateFlow<RingState?>(null)
        val state = mutableState.asStateFlow()
        var instance: RingService? = null; private set
    }
}

