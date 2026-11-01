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
            LaunchedEffect(Unit) { delay(1500); loading = false }
            var seen by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(state) { if (state != null) seen = true }
            KkaeumTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = state
                    if (current == null) Box(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                            AlarmClockDisplay()
                            Text(if (seen) "알람 종료" else if (loading) "불러오는 중…" else "울리는 알람 없음",
                                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                            Button(onClick = { finish() }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
                                Text("닫기", fontSize = 26.sp)
                            }
                        }
                    } else {
                        var enteredKey by rememberSaveable { mutableStateOf("") }
                        LaunchedEffect(current.key) { if (enteredKey != current.key) { enteredKey = current.key; RingService.instance?.interact() } }
                        var answer by rememberSaveable(current.key, current.completed) { mutableStateOf("") }
                        BoxWithConstraints(Modifier.safeDrawingPadding().padding(20.dp)) {
                            val keyHeight = if (maxWidth < 700.dp) 56.dp else if (maxHeight < 520.dp) 64.dp else 88.dp
                            val compactClock = maxHeight < 520.dp
                            val summary: @Composable () -> Unit = {
                                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    Text(if (current.preview) "알람 미리보기" else current.alarm.name, color = Muted)
                                    AlarmClockDisplay(compactClock)
                                    Text("수학 미션 · ${current.completed + 1} / ${current.total} 문제", style = MaterialTheme.typography.titleLarge)
                                    Text("${current.problem.left} + ${current.problem.right}", style = MaterialTheme.typography.displayLarge)
                                    Text(answer.ifEmpty { "답을 입력하세요" }, style = MaterialTheme.typography.headlineMedium)
                                    Text(if (current.quiet) "입력 없이 ${current.alarm.quietSeconds}초가 지나면 다시 울립니다" else "숫자를 누르면 잠시 조용해집니다", color = Muted)
                                    if (current.message.isNotEmpty()) Text(current.message, color = MaterialTheme.colorScheme.error)
                                    if (current.preview) Text("알람 음량은 기기 설정을 따릅니다", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    if (current.preview) OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { RingService.instance?.stopPreview(); finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { Text("미리보기 닫기", fontSize = 22.sp) }
                                    else if (current.used < current.alarm.snoozeLimit) OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { RingService.instance?.snooze() }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                                        Text("${current.alarm.snoozeMinutes}분 뒤 다시 울림 · ${current.alarm.snoozeLimit - current.used}회 남음", fontSize = 22.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            val keypad: @Composable () -> Unit = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("지움","0","확인")).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { key ->
                                            Button(onClick = {
                                                when (key) {
                                                    "확인" -> { RingService.instance?.submit(answer.toIntOrNull()); answer = "" }
                                                    "지움" -> { RingService.instance?.interact(); answer = answer.dropLast(1) }
                                                    else -> { RingService.instance?.interact(); if (answer.length < 3) answer += key }
                                                }
                                            }, shape = MaterialTheme.shapes.large, colors = ButtonDefaults.buttonColors(
                                                containerColor = if (key == "확인") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = if (key == "확인") MaterialTheme.colorScheme.onPrimary else Ink),
                                                modifier = Modifier.weight(1f).heightIn(min = keyHeight)) { Text(key, fontSize = 32.sp) }
                                        } }
                                    }
                                }
                            }
                            if (maxWidth >= 700.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { summary() }
                                Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { keypad() }
                            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                AlarmClockDisplay(compact = true)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${current.problem.left} + ${current.problem.right}", style = MaterialTheme.typography.displaySmall)
                                    Text((if (current.preview) "미리보기 · " else "") + "${current.completed + 1} / ${current.total}", color = Muted)
                                }
                                Text(answer.ifEmpty { "답을 입력하세요" }, style = MaterialTheme.typography.headlineSmall)
                                Text(if (current.quiet) "입력 없으면 ${current.alarm.quietSeconds}초 후 다시 울림" else "숫자를 누르면 잠시 조용해집니다",
                                    style = MaterialTheme.typography.bodySmall, color = Muted)
                                if (current.message.isNotEmpty()) Text(current.message, color = MaterialTheme.colorScheme.error)
                                keypad()
                                if (current.preview) OutlinedButton(shape = MaterialTheme.shapes.medium,
                                    onClick = { RingService.instance?.stopPreview(); finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text("미리보기 닫기")
                                } else if (current.used < current.alarm.snoozeLimit) OutlinedButton(shape = MaterialTheme.shapes.medium,
                                    onClick = { RingService.instance?.snooze() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text("${current.alarm.snoozeMinutes}분 뒤 다시 울림 · ${current.alarm.snoozeLimit - current.used}회 남음")
                                }
                            }
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
