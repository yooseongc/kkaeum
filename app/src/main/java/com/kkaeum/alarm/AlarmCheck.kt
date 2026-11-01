package com.kkaeum.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class AlarmCheck(val exact: Boolean, val notifications: Boolean, val fullScreen: Boolean,
    val volume: Int, val maxVolume: Int, val minVolume: Int = 0) {
    val issues: List<String> get() = buildList {
        if (!exact) add("정확한 알람 허용이 필요합니다")
        if (!notifications) add("알람 알림이 꺼져 있습니다")
        if (!fullScreen) add("잠금 화면 표시 허용이 필요합니다")
        if (volume == 0) add("알람 음량이 0입니다")
    }
    companion object {
        fun read(context: Context): AlarmCheck {
            val notifications = context.getSystemService(NotificationManager::class.java)
            val audio = context.getSystemService(AudioManager::class.java)
            val channel = notifications.getNotificationChannel(RingService.CHANNEL)
            return AlarmCheck(AlarmScheduler(context).allowed(), notifications.areNotificationsEnabled() &&
                channel?.importance != NotificationManager.IMPORTANCE_NONE,
                Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent(),
                audio.getStreamVolume(AudioManager.STREAM_ALARM), audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), audio.getStreamMinVolume(AudioManager.STREAM_ALARM))
        }
    }
}
@Composable fun AlarmCheckDialog(check: AlarmCheck, exactAction: () -> Unit, notificationAction: () -> Unit,
    fullAction: () -> Unit, volumeAction: (Int) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("알람 점검") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CheckItem("정확한 알람", check.exact, "허용됨", "허용 필요", exactAction)
            CheckItem("알람 알림", check.notifications, "켜짐", "꺼짐", notificationAction)
            CheckItem("잠금 화면 표시", check.fullScreen, "허용됨", "허용 필요", fullAction)
            if (!check.fullScreen) Text("잠금 화면에서 미션 화면을 바로 표시할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("알람 음량 · ${check.volume} / ${check.maxVolume}")
            if (check.volume == 0) Text("알람 소리가 나지 않습니다", color = MaterialTheme.colorScheme.error)
            var level by remember(check.volume) { mutableFloatStateOf(check.volume.toFloat()) }
            Slider(modifier = Modifier.semantics { contentDescription = "알람 음량" }, value = level.coerceIn(check.minVolume.toFloat(), check.maxVolume.toFloat()), onValueChange = { level = it },
                onValueChangeFinished = { volumeAction(level.roundToInt()) },
                valueRange = check.minVolume.toFloat()..check.maxVolume.coerceAtLeast(check.minVolume + 1).toFloat(),
                enabled = check.maxVolume > check.minVolume,
                steps = (check.maxVolume - check.minVolume - 1).coerceAtLeast(0))
            Text("기기의 알람 음량에 적용됩니다", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("닫기") } })
}
@Composable private fun CheckItem(title: String, ok: Boolean, good: String, bad: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(if (ok) good else bad,
            color = if (ok) Muted else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!ok) TextButton(onClick = action) { Text("설정 열기") }
    }
}
