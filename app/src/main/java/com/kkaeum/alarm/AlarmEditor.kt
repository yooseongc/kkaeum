package com.kkaeum.alarm

import kotlinx.coroutines.*
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalLayoutApi::class)
@Composable fun AlarmEditor(alarm: Alarm, changed: (Alarm) -> Unit, save: () -> Unit,
    test: () -> Unit, report: (String) -> Unit, canSave: Boolean, previewMission: (Alarm) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val audio = remember { AlarmAudio(context) }
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var soundDialog by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) { if (playing) { kotlinx.coroutines.delay(10_000); audio.stop(); playing = false } }
    DisposableEffect(owner, audio) {
        val listener = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) { audio.stop(); playing = false } }
        owner.lifecycle.addObserver(listener)
        onDispose { owner.lifecycle.removeObserver(listener); audio.stop() }
    }
    LaunchedEffect(alarm.soundUri, alarm.id) { audio.stop(); playing = false }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION") val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val name = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: "선택한 알람음"
                changed(alarm.copy(soundUri = uri.toString(), soundName = name))
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "내 음원"
                    AlarmSounds.import(context, uri) to name
                }
                changed(alarm.copy(soundUri = result.first, soundName = result.second))
                report("알람음을 추가했어요")
            } catch (e: Exception) { report("음원을 가져오지 못했어요. 25MB 이하의 오디오 파일을 선택해 주세요.") }
            finally { importing = false }
        }
    }
    if (soundDialog) AlertDialog(onDismissRequest = { soundDialog = false }, title = { Text("깨움 알람음") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            AlarmSounds.builtins.forEach { sound ->
                TextButton(onClick = { changed(alarm.copy(soundUri = sound.uri, soundName = sound.title)); soundDialog = false }) {
                    Column(Modifier.fillMaxWidth()) { Text(sound.title); Text(sound.description, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } }, confirmButton = { TextButton(onClick = { soundDialog = false }) { Text("닫기") } })

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (alarm.id == 0) "새 알람" else "알람 설정", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = save, enabled = canSave && !importing, shape = MaterialTheme.shapes.medium, modifier = Modifier.heightIn(min = 48.dp)) { Text("저장") }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val twoColumns = maxWidth >= 720.dp
            val daysPerRow = if ((twoColumns && maxWidth < 900.dp) || (!twoColumns && maxWidth < 420.dp)) 4 else 7
            val timeSection: @Composable () -> Unit = {
            SettingCard("시간", "24시간 형식") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Wheel(alarm.hour, 23, "시 선택") { changed(alarm.copy(hour = it)) }
                    Text(":", style = MaterialTheme.typography.displaySmall)
                    Wheel(alarm.minute, 59, "분 선택") { changed(alarm.copy(minute = it)) }
                }
                OutlinedTextField(alarm.name, { changed(alarm.copy(name = it.take(40))) }, label = { Text("알람 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(alarm.groupName, { changed(alarm.copy(groupName=it.take(24))) }, label={ Text("그룹") }, placeholder={ Text("미지정") }, singleLine=true, modifier=Modifier.fillMaxWidth())
                FlowRow(maxItemsInEachRow = daysPerRow, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("월","화","수","목","금","토","일").forEachIndexed { index, label ->
                        FilterChip(alarm.days and (1 shl index) != 0, { changed(alarm.copy(days = alarm.days xor (1 shl index), skipDate = "")) }, label = { Text(label) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("평일" to 31, "주말" to 96, "매일" to 127, "한 번만" to 0).forEach { (label, value) ->
                        TextButton(onClick = { changed(alarm.copy(days = value, skipDate = "")) }) { Text(label) }
                    }
                }
            }
            }
            val soundSection: @Composable () -> Unit = {
            SettingCard("소리와 진동", alarm.soundName) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { soundDialog = true }) { Text("깨움 알람음") }
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = {
                        runCatching { soundPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (alarm.soundUri.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else if (alarm.soundUri.startsWith("kkaeum:" ) || alarm.soundUri.startsWith("kkaeum-file:")) null else Uri.parse(alarm.soundUri))) }
                            .onFailure { report("기기 소리 목록을 열지 못했어요. 파일에서 선택해 주세요.") }
                    }) { Text("기기 알람음") }
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { filePicker.launch(arrayOf("audio/*")) }, enabled = !importing) { Text(if (importing) "가져오는 중…" else "내 음원") }
                    TextButton(onClick = {
                        if (playing) { audio.stop(); playing = false } else {
                            playing = true; audio.play(alarm.soundUri) { report("선택한 소리를 재생할 수 없어 기본음을 사용해요.") }
                        }
                    }) { Text(if (playing) "듣기 중지" else "미리 듣기") }
                }
                ToggleRow("진동", null, alarm.vibrate) { changed(alarm.copy(vibrate = it)) }
            }
            }
            val holidaySection: @Composable () -> Unit = {
                SettingCard("휴일") {
                    ToggleRow("한국 공휴일 건너뛰기", "대체공휴일 포함", alarm.skipHolidays) { changed(alarm.copy(skipHolidays = it)) }
                }
            }
            val missionSection: @Composable () -> Unit = {
                MissionSettings(alarm, changed) { audio.stop(); playing = false; previewMission(alarm) }
            }
            val snoozeSection: @Composable () -> Unit = {
            SettingCard("다시 울림") {
                ToggleRow("사용", null, alarm.snoozeLimit != 0) { changed(alarm.copy(snoozeLimit = if (it) 3 else 0)) }
                if (alarm.snoozeLimit != 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 5, 10, 15).forEach { m -> FilterChip(alarm.snoozeMinutes == m, { changed(alarm.copy(snoozeMinutes = m)) }, label = { Text("${m}분") }) }
                    }
                    ToggleRow("무제한", null, alarm.snoozeLimit == SnoozePolicy.UNLIMITED) {
                        changed(alarm.copy(snoozeLimit = if (it) SnoozePolicy.UNLIMITED else 3))
                    }
                    if (alarm.snoozeLimit != SnoozePolicy.UNLIMITED) {
                        Stepper("최대 횟수", alarm.snoozeLimit, 1..SnoozePolicy.MAX_LIMIT, "회") { changed(alarm.copy(snoozeLimit = it)) }
                    }
                }
            }
            }
            val footer: @Composable () -> Unit = {
            OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { audio.stop(); playing = false; test() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("알람 미리보기") }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (twoColumns) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        timeSection(); missionSection()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        soundSection(); snoozeSection(); holidaySection(); footer()
                    }
                } else {
                    timeSection(); soundSection(); missionSection(); snoozeSection(); holidaySection(); footer()
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
@Composable fun ToggleRow(title: String, subtitle: String?, checked: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title); if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, changed, modifier = Modifier.semantics { contentDescription = title })
    }
}
@Composable fun Stepper(label: String, value: Int, range: IntRange, unit: String, changed: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { changed(value - 1) }, enabled = value > range.first, contentPadding = PaddingValues(8.dp), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "$label 줄이기" }) { Text("−") }
        Text("$value$unit", Modifier.padding(horizontal = 8.dp))
        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { changed(value + 1) }, enabled = value < range.last, contentPadding = PaddingValues(8.dp), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "$label 늘리기" }) { Text("+") }
    }
}
