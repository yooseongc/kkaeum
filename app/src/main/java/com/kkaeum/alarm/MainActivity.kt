package com.kkaeum.alarm

import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.semantics.*
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var permissionRevision by mutableIntStateOf(0)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    override fun onResume() { super.onResume(); permissionRevision++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AlarmViewModel = viewModel()
            val options = appOptions()
            val alarms by vm.alarms.collectAsStateWithLifecycle()
            val holidays by vm.holidays.collectAsStateWithLifecycle()
            val pauses by vm.pauses.collectAsStateWithLifecycle()
            var management by rememberSaveable { mutableStateOf<String?>(null) }
            var missionPreviewConfig by rememberSaveable { mutableStateOf<String?>(null) }
            val rules by vm.rules.collectAsStateWithLifecycle()
            val years by vm.years.collectAsStateWithLifecycle()
            val snoozes by vm.snoozes.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val refreshing by vm.refreshing.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            val active by RingService.state.collectAsStateWithLifecycle()
            var checkOpen by rememberSaveable { mutableStateOf(false) }
            var check by remember { mutableStateOf(AlarmCheck.read(this@MainActivity)) }
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) { check = AlarmCheck.read(this@MainActivity); delay(1000) }
                }
            }
            var now by remember { mutableStateOf(ZonedDateTime.now()) }
            LaunchedEffect(permissionRevision) {
                while (true) { now = ZonedDateTime.now(); delay(30_000) }
            }
            var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
            var baseline by rememberSaveable { mutableStateOf<String?>(null) }
            var showEditor by rememberSaveable { mutableStateOf(false) }
            var pendingTarget by rememberSaveable { mutableStateOf<String?>(null) }
            var deleting by rememberSaveable { mutableStateOf<String?>(null) }
            var settings by rememberSaveable { mutableStateOf(false) }
            var calendar by rememberSaveable { mutableStateOf(false) }
            val draft = draftJson?.let(AlarmCodec::decode)
            val dirty = draftJson != baseline
            fun applyTarget(target: String) {
                when (target) {
                    "LIST" -> { settings = false; showEditor = false; draftJson = null; baseline = null }
                    "SETTINGS" -> { settings = true; showEditor = false }
                    else -> { settings = false; draftJson = target; baseline = target; showEditor = true }
                }
            }
            fun choose(target: String) { if (dirty) pendingTarget = target else applyTarget(target) }
            fun syncSetting(a: Alarm, skipOnly: Boolean = false) {
                if (draft?.id == a.id) {
                    val updated = if (skipOnly) draft.copy(skipDate = a.skipDate) else draft.copy(enabled = a.enabled)
                    draftJson = AlarmCodec.encode(updated)
                    baseline = baseline?.let(AlarmCodec::decode)?.let { b -> AlarmCodec.encode(if (skipOnly) b.copy(skipDate = a.skipDate) else b.copy(enabled = a.enabled)) }
                }
            }
            val holidayMap = remember(holidays) { holidays.associate { it.date to it.name } }
            val ruleMap = remember(rules) { rules.associate { it.date to it.force } }
            fun next(a: Alarm) = NextAlarm.calculate(a, now, holidayMap, ruleMap, pauses)
            val upcoming = upcomingAlarm(alarms, snoozes, now, holidayMap, ruleMap, pauses)
            KkaeumTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    BoxWithConstraints(Modifier.widthIn(max = 1440.dp).fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                        val wide = maxWidth >= 1000.dp
                        BackHandler(enabled = settings || showEditor) { choose("LIST") }
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("깨움", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { choose("SETTINGS") }) { Text("설정") }
                                    Button(onClick = { choose(AlarmCodec.encode(Alarm())) }, enabled = ready, shape = MaterialTheme.shapes.medium, modifier = Modifier.heightIn(min = 48.dp)) { Text("+ 새 알람") }
                                }
                            }
                            if (!settings && check.issues.isNotEmpty()) Surface(
                                color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(check.issues.first() + if (check.issues.size > 1) " 외 ${check.issues.size - 1}건" else "",
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { checkOpen = true }) { Text("확인") }
                                }
                            }
                            if (active != null) Button(onClick = { startActivity(Intent(this@MainActivity, MissionActivity::class.java)) }) { Text("울리는 알람 열기") }
                            if (message.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Muted)
                                TextButton(onClick = { vm.message.value = "" }) { Text("닫기") }
                            }
                            val list: @Composable () -> Unit = {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (!showEditor) item { AlarmClockDisplay(compact = true) }
                                    item {
                                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line)) {
                                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(if (upcoming?.snooze == true) "다시 울림" else "다음 알람", color = Muted)
                                            Text(upcoming?.let { displayTime(it.time.toLocalTime(), options) } ?: "—", color = Ink, style = MaterialTheme.typography.displaySmall)
                                            Text(upcoming?.let { alarmDateLabel(it.time, now) + " · " + remainingLabel(it.time, now) } ?: "켜진 알람 없음", color = Ink, style = MaterialTheme.typography.bodySmall)
                                            upcoming?.let { Text(it.alarm.name, color = Muted, style = MaterialTheme.typography.bodyMedium) }
                                        }
                                    }
                                    }
                                        if (alarms.isEmpty()) item { Text("등록된 알람이 없습니다", color = Muted) }
                                        items(alarms, key = { it.id }) { a ->
                                            val occurrence = next(a)
                                            var menu by rememberSaveable(a.id) { mutableStateOf(false) }
                                            Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (showEditor && draft?.id == a.id) MaterialTheme.colorScheme.primary else Line), color = if (showEditor && draft?.id == a.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.fillMaxWidth().clickable { choose(AlarmCodec.encode(a)) }) {
                                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(a.name, color = Muted)
                                                            if(a.groupName.isNotEmpty()) Text(a.groupName, style=MaterialTheme.typography.labelSmall, color=Muted)
                                                            Text(displayTime(LocalTime.of(a.hour, a.minute), options), color = if (a.enabled) Ink else Muted,
                                                                style = MaterialTheme.typography.headlineLarge)
                                                        }
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Switch(a.enabled, { vm.save(a.copy(enabled = it)) { syncSetting(it) } }, enabled = ready, modifier = Modifier.semantics { contentDescription = "${a.name} 알람" })
                                                            Text(if (a.enabled) "켜짐" else "꺼짐", style = MaterialTheme.typography.labelSmall, color = Muted)
                                                        }
                                                        Box {
                                                            IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "${a.name} 더보기" }) { Text("⋯", fontSize = 26.sp) }
                                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                                DropdownMenuItem(text = { Text("복제") }, onClick = {
                                                                    menu = false; choose(AlarmCodec.encode(a.copy(id = 0, name = "${a.name} 복사", enabled = false, skipDate = "")))
                                                                })
                                                                DropdownMenuItem(text = { Text("삭제") }, onClick = { menu = false; deleting = AlarmCodec.encode(a) })
                                                            }
                                                        }
                                                    }
                                                    Text("${dayLabel(a.days)} · ${MissionTypes.summary(a)}", style = MaterialTheme.typography.bodySmall)
                                                    if (a.enabled) {
                                                        Text("다음: " + nextLabel(occurrence.time, now, options), style = MaterialTheme.typography.bodySmall)
                                                        occurrence.skipped.firstOrNull()?.let { Text("${it.date} · ${it.reason} 건너뜀", style = MaterialTheme.typography.bodySmall, color = Muted) }
                                                        if (a.skipHolidays && years.none { it.year == occurrence.time.year }) Text("${occurrence.time.year}년 공휴일 정보가 없습니다. 휴일 설정에서 업데이트해 주세요.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                                        TextButton(onClick = { vm.skip(a) { syncSetting(it, true) } }, contentPadding = PaddingValues(0.dp)) {
                                                            Text(if (a.skipDate >= now.toLocalDate().toString()) "한 번 건너뛰기 취소" else "다음 한 번 건너뛰기")
                                                        }
                                                    }
                                                    snoozes.find { it.alarmId == a.id }?.let { s ->
                                                        Text("다시 울림: " + nextLabel(Instant.ofEpochMilli(s.due).atZone(now.zone), now, options), color = MaterialTheme.colorScheme.primary)
                                                        TextButton(onClick = { vm.cancelSnooze(a.id) }) { Text("다시 울림 취소") }
                                                    }

                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            val editor: @Composable () -> Unit = {
                                Column(Modifier.fillMaxSize()) {
                                    TextButton(onClick = { choose("LIST") }) { Text("← 알람 목록") }
                                    draft?.let { a ->
                                        AlarmEditor(a, changed = { draftJson = AlarmCodec.encode(it) }, save = {
                                            vm.save(a) { saved -> draftJson = AlarmCodec.encode(saved); baseline = draftJson }
                                        }, test = {
                                            if (active != null) vm.message.value = "현재 울리는 알람을 먼저 완료해 주세요."
                                            else runCatching {
                                                startForegroundService(Intent(this@MainActivity, RingService::class.java).putExtra("config", AlarmCodec.encode(a)).putExtra("preview", true))
                                                startActivity(Intent(this@MainActivity, MissionActivity::class.java))
                                            }.onFailure { vm.message.value = "미리보기를 열지 못했어요. 다시 시도해 주세요." }
                                        }, report = { vm.message.value = it }, canSave = ready,
                                            previewMission = { missionPreviewConfig = AlarmCodec.encode(it) })
                                    }
                                }
                            }
                            if (settings) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Box(Modifier.widthIn(max = 800.dp).fillMaxSize()) {
                                    SettingsScreen(options, appearance = {
                                        runCatching { AppPreferences.appearance(this@MainActivity, it) }
                                            .onFailure { vm.message.value = "설정을 저장하지 못했어요. 다시 시도해 주세요." }
                                    }, manage = { vm.message.value=""; management=it }, calendar = { calendar = true }, check = { checkOpen = true }, back = { choose("LIST") })
                                }
                            } else if (wide && showEditor) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Box(Modifier.width(300.dp).fillMaxHeight()) { list() }
                                Box(Modifier.weight(1f).fillMaxHeight()) { editor() }
                            } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                if (showEditor) editor() else Box(Modifier.widthIn(max = 800.dp).fillMaxSize()) { list() }
                            }
                        }
                    }
                }
                }
                ManagementDialogs(management, { management=null }, vm)
                missionPreviewConfig?.let { config -> MissionPreview(AlarmCodec.decode(config)) { missionPreviewConfig = null } }
                if (checkOpen) AlarmCheckDialog(check,
                    exactAction = { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) },
                    notificationAction = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) },
                    fullAction = { if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) },
                    volumeAction = { level ->
                        runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, level, 0) }
                            .onFailure { vm.message.value = "음량을 변경하지 못했습니다. 기기 소리 설정을 확인해 주세요." }
                        check = AlarmCheck.read(this@MainActivity)
                    }, dismiss = { checkOpen = false })
                if (calendar) CalendarDialog(holidays, rules, years, refreshing, vm::refresh, vm::rule) { calendar = false }
                pendingTarget?.let { target -> AlertDialog(onDismissRequest = { pendingTarget = null },
                    title = { Text("수정 중인 설정을 버릴까요?") }, text = { Text("저장하지 않은 변경사항이 있습니다.") },
                    confirmButton = { TextButton(onClick = { draftJson = baseline; applyTarget(target); pendingTarget = null }) { Text("버리기") } },
                    dismissButton = { TextButton(onClick = { pendingTarget = null }) { Text("계속 편집") } }) }
                deleting?.let { json -> val a = AlarmCodec.decode(json)
                    AlertDialog(onDismissRequest = { deleting = null }, title = { Text("‘${a.name}’ 알람을 삭제할까요?") },
                        confirmButton = { TextButton(onClick = { vm.delete(a) {
                            if (draft?.id == a.id) { draftJson = AlarmCodec.encode(Alarm()); baseline = draftJson; showEditor = false }; deleting = null
                        } }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
                }
            }
        }
    }
}
