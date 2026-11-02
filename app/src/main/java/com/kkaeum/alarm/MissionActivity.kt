package com.kkaeum.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val state by RingService.state.collectAsStateWithLifecycle()
            var loading by remember { mutableStateOf(true) }
            var seen by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(1500); loading = false }
            LaunchedEffect(state?.key) { if (state != null) { seen = true; RingService.instance?.interact() } }
            KkaeumTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = state
                    if (current == null) Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        AlarmClockDisplay()
                        Text(if (seen) "알람 종료" else if (loading) "불러오는 중…" else "울리는 알람 없음", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = { finish() }, shape = MaterialTheme.shapes.medium, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 80.dp)) { Text("닫기", fontSize = 26.sp) }
                    } else key(current.key) {
                        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                            val compactClock = maxWidth < 800.dp || maxHeight < 540.dp
                            val summary: @Composable () -> Unit = {
                                Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (current.preview) "알람 미리보기" else current.alarm.name, style = MaterialTheme.typography.titleLarge)
                                    AlarmClockDisplay(compact = compactClock)
                                    Text(if (current.quiet) "입력 없으면 ${current.alarm.quietSeconds}초 후 다시 울림" else "화면을 누르면 잠시 조용해집니다", color = Muted)
                                    if (current.preview) OutlinedButton(onClick = { RingService.instance?.stopPreview(); finish() }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("미리보기 닫기") }
                                    else if (SnoozePolicy.canSnooze(current.alarm.snoozeLimit, current.used)) OutlinedButton(onClick = { RingService.instance?.snooze() }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                                        val remaining = if (current.alarm.snoozeLimit == SnoozePolicy.UNLIMITED) "무제한" else "${current.alarm.snoozeLimit - current.used}회 남음"
                                        Text("${current.alarm.snoozeMinutes}분 뒤 다시 울림 · $remaining", textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            val puzzle: @Composable () -> Unit = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CompositionLocalProvider(LocalMissionInteraction provides { RingService.instance?.interact() }) {
                                        MissionPanel(current.alarm, current.missionState) { action ->
                                            when (action) {
                                                is MissionAction.Answer -> RingService.instance?.submit(action.value)
                                                is MissionAction.Select -> RingService.instance?.select(action.index)
                                            }
                                        }
                                    }
                                    if (current.message.isNotEmpty()) Text(current.message, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (maxWidth >= 800.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(0.8f).verticalScroll(rememberScrollState())) { summary() }
                                Box(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) { puzzle() }
                            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) { summary(); puzzle() }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun AlarmClockDisplay(compact: Boolean = false) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    val options = appOptions()
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val clockSize = (maxWidth.value / LocalDensity.current.fontScale / (if (options.clock24) 5.4f else 7.5f)).coerceIn(28f, if (compact) 48f else 80f).sp
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(now.format(dateFormat), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = if (compact) 16.sp else 24.sp, color = Muted)
            Text(displayTime(now.toLocalTime(), options, seconds = true), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = clockSize, lineHeight = clockSize * 1.15f, fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
        }
    }
}
