package com.kkaeum.alarm

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun ManagementDialogs(mode: String?, close: () -> Unit, vm: AlarmViewModel) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val status by vm.message.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val periods by vm.pauses.collectAsStateWithLifecycle()
    val revision by AlarmHistory.revision.collectAsState()
    val events=remember(revision,mode) { AlarmHistory.read(context).asReversed() }
    var backup by remember { mutableStateOf<BackupData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    var clearHistory by remember { mutableStateOf(false) }
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    fun pick(date: LocalDate, picked: (LocalDate)->Unit) {
        DatePickerDialog(context, if(dark) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog,
            { _,y,m,d -> picked(LocalDate.of(y,m+1,d)) },date.year,date.monthValue-1,date.dayOfMonth).show()
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) scope.launch {
            busy=true; error=""
            try { withContext(Dispatchers.IO) {
                val text=AlarmBackup.export(context)
                AlarmBackup.parse(text) // Never write a backup that this version cannot import.
                context.contentResolver.openOutputStream(uri,"wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }; vm.message.value="백업 파일을 저장했습니다" }
            catch(e: Exception) { error="백업 파일을 저장하지 못했습니다" }
            finally { busy=false }
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            busy=true; error=""
            try { backup=withContext(Dispatchers.IO) {
                val bytes=context.contentResolver.openInputStream(uri)!!.use { input ->
                    val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                    while(true) { val count=input.read(buffer); if(count<0) break
                        require(output.size()+count<=AlarmBackup.MAX_BYTES); output.write(buffer,0,count) }
                    output.toByteArray()
                }
                require(bytes.size<=AlarmBackup.MAX_BYTES)
                AlarmBackup.parse(bytes.toString(Charsets.UTF_8))
            } } catch(e: Exception) { error="올바른 깨움 백업 파일을 선택해 주세요 (최대 1MB)" }
            finally { busy=false }
        }
    }
    if(mode!=null) AlertDialog(onDismissRequest={ if(!busy) { error=""; close() } },
        title={ Text(when(mode) { "backup"->"백업 · 복원"; "periods"->"기간 건너뛰기"; "groups"->"알람 그룹"; else->"울림 기록" }) },
        text={ LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) {
            if(status.isNotEmpty()) item { Text(status,style=MaterialTheme.typography.bodySmall) }
            if(error.isNotEmpty()) item { Text(error,color=MaterialTheme.colorScheme.error) }
            if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            when(mode) {
                "backup" -> {
                    item { Text("알람·그룹·날짜별 예외·기간·표시 설정을 파일로 저장합니다.") }
                    item { Text("내 음원 파일과 기기 알람음은 포함하지 않습니다. 복원 시 기본 알람음으로 바뀌며, 깨움 내장음은 유지됩니다.",style=MaterialTheme.typography.bodySmall) }
                    item { OutlinedButton(shape=MaterialTheme.shapes.medium,onClick={ export.launch("kkaeum-${LocalDate.now()}.json") },enabled=!busy) { Text("백업 저장") } }
                    item { OutlinedButton(shape=MaterialTheme.shapes.medium,onClick={ import.launch(arrayOf("application/json","text/plain","application/octet-stream")) },enabled=!busy) { Text("백업 파일 열기") } }
                }
                "periods" -> {
                    item { Text("시작일과 종료일 모두 건너뜁니다. 켜진 모든 알람에 적용되며, 날짜별 ‘울림’보다 우선합니다.") }
                    item { Text("해당 기간의 다시 울림 예약은 취소됩니다. 현재 울리는 알람은 유지됩니다.",style=MaterialTheme.typography.bodySmall) }
                    item { OutlinedButton(shape=MaterialTheme.shapes.medium,onClick={ pick(start) { start=it; if(end<start) end=start } }) { Text("시작 · $start") } }
                    item { OutlinedButton(shape=MaterialTheme.shapes.medium,onClick={ pick(end) { end=it } }) { Text("종료 · $end") } }
                    item { Button(shape=MaterialTheme.shapes.medium,onClick={
                        val period=PausePeriod(start.toString(),end.toString())
                        runCatching { validatePause(period) }.onSuccess { vm.pause(period); error="" }
                            .onFailure { error="종료일은 시작일 이후, 최대 367일, 앞으로 2년 이내로 선택해 주세요" }
                    }) { Text("기간 저장") } }
                    if(periods.isEmpty()) item { Text("설정한 기간이 없습니다",color=Muted) }
                    items(periods,key={it.start}) { period ->
                        Column { Text("${period.start} ~ ${period.end}")
                            if(period.end<LocalDate.now().toString()) Text("종료됨",color=Muted)
                            TextButton(onClick={ vm.pause(period,true) }) { Text("삭제") } }
                    }
                }
                "groups" -> {
                    item { Text("알람 편집에서 그룹 이름을 지정할 수 있습니다.",style=MaterialTheme.typography.bodySmall) }
                    val groups=alarms.filter { it.groupName.isNotEmpty() }.groupBy { it.groupName }.toSortedMap()
                    if(groups.isEmpty()) item { Text("등록된 그룹이 없습니다",color=Muted) }
                    groups.forEach { (name,rows) -> item(key=name) {
                        Column {
                            Text(name,style=MaterialTheme.typography.titleMedium)
                            Text("${rows.size}개 중 ${rows.count { it.enabled }}개 켜짐",color=Muted)
                            Row { TextButton(onClick={ vm.group(name,true) }) { Text("모두 켜기") }
                                TextButton(onClick={ vm.group(name,false) }) { Text("모두 끄기") } }
                        }
                    } }
                }
                else -> {
                    item { Text("최근 30일, 최대 500건 · 이 기기에만 저장",style=MaterialTheme.typography.bodySmall,color=Muted) }
                    item { Text("‘울림 시작’은 앱 실행 기록이며 실제 소리의 청취 여부를 확인하는 기록은 아닙니다.",style=MaterialTheme.typography.bodySmall,color=Muted) }
                    if(events.isEmpty()) item { Text("울림 기록이 없습니다") }
                    items(events,key={it.key}) { event ->
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("${event.name} · ${event.action}")
                            Text("실행: " + eventTime(event.at),style=MaterialTheme.typography.bodySmall,color=Muted)
                            if(event.due>0) Text("예정: " + eventTime(event.due),style=MaterialTheme.typography.bodySmall,color=Muted)
                            HorizontalDivider()
                        }
                    }
                    if(events.isNotEmpty()) item { TextButton(onClick={ clearHistory=true }) { Text("기록 지우기") } }
                }
            }
        } }, confirmButton={ TextButton(onClick={ error=""; close() },enabled=!busy) { Text("닫기") } })
    backup?.let { data -> AlertDialog(onDismissRequest={ if(!busy) backup=null },title={ Text("백업을 가져올까요?") },
        text={ Text("알람 ${data.alarms.size}개 · 날짜 예외 ${data.rules.size}개 · 기간 ${data.pauses.size}개\n\n알람은 꺼진 상태로 추가됩니다. 같은 날짜·시작일의 기존 설정은 유지하고, 표시 설정은 백업 값으로 바뀝니다. 추가한 날짜 예외와 기간은 기존 알람에도 적용됩니다.") },
        confirmButton={ TextButton(enabled=!busy,onClick={ scope.launch {
            busy=true
            try { vm.restore(data); backup=null; close() }
            catch(e: Exception) { backup=null; error="가져오기를 완료하지 못했습니다. 목록과 권한을 확인해 주세요." }
            finally { busy=false }
        } }) { Text("가져오기") } }, dismissButton={ TextButton(enabled=!busy,onClick={ backup=null }) { Text("취소") } }) }
    if(clearHistory) AlertDialog(onDismissRequest={clearHistory=false},title={Text("울림 기록을 지울까요?")},
        confirmButton={TextButton(onClick={ runCatching { AlarmHistory.clear(context) }.onFailure { error="기록을 지우지 못했습니다" }; clearHistory=false }){Text("지우기")}},
        dismissButton={TextButton(onClick={clearHistory=false}){Text("취소")}})
}
private fun eventTime(value: Long)=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm:ss"))
