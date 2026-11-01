package com.kkaeum.alarm

import android.app.DatePickerDialog
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CalendarDialog(holidays: List<Holiday>, rules: List<DateRule>, years: List<HolidayYear>, refreshing: Boolean,
    refresh: () -> Unit, rule: (String, Boolean?) -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    val pickerTheme = if (MaterialTheme.colorScheme.background.luminance() < .5f) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var force by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var help by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth(.94f).fillMaxHeight(.9f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("휴일", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = dismiss) { Text("닫기") }
                }
                TabRow(tab) { listOf("날짜별 설정", "한국 공휴일").forEachIndexed { i, label ->
                    Tab(tab == i, { tab = i }, text = { Text(label) })
                } }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab == 0) {
                        item {
                            Text("켜진 모든 알람에 적용", color = Muted, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { help = true }, contentPadding = PaddingValues(0.dp)) { Text("적용 방식") }
                        }
                        item {
                            OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = {
                                val d = LocalDate.parse(selectedDate)
                                DatePickerDialog(context, pickerTheme, { _, y, m, day -> selectedDate = LocalDate.of(y, m + 1, day).toString() },
                                    d.year, d.monthValue - 1, d.dayOfMonth).show()
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(selectedDate) }
                        }
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!force, { force = false }, label = { Text("울리지 않음") })
                                FilterChip(force, { force = true }, label = { Text("울림") })
                                Button(onClick = { rule(selectedDate, force) }, shape = MaterialTheme.shapes.medium) { Text("추가") }
                            }
                        }
                        if (rules.isEmpty()) item { Text("설정한 날짜가 없습니다", color = Muted) }
                        items(rules, key = { it.date }) { r ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(r.date); Text(if (r.force) "울림" else "울리지 않음", color = Muted) }
                                TextButton(onClick = { rule(r.date, null) }) { Text("삭제") }
                            }
                            HorizontalDivider(color = Line)
                        }
                    } else {
                        item {
                            Text("공휴일 건너뛰기에 사용하는 목록", style = MaterialTheme.typography.bodySmall, color = Muted)
                            TextButton(onClick = { help = true }, contentPadding = PaddingValues(0.dp)) { Text("공휴일 정보") }
                        }
                        items(years, key = { "year-${it.year}" }) { y ->
                            val label = if (y.updatedAt == 0L) "기본 목록" else Instant.ofEpochMilli(y.updatedAt).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("M/d HH:mm 업데이트"))
                            Text("${y.year}년 · $label", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        item { OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = refresh, enabled = !refreshing) { Text(if (refreshing) "업데이트 중…" else "공휴일 업데이트") } }
                        items(holidays, key = { it.date }) { h ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(h.date, color = Muted); Text(h.name, Modifier.weight(1f))
                            }
                            HorizontalDivider(Modifier.padding(top = 12.dp), color = Line)
                        }
                    }
                }
            }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(if (tab == 0) "날짜별 설정" else "공휴일 정보") },
        text = { Text(if (tab == 0) "켜진 모든 알람에 적용됩니다.\n\n‘울림’을 선택하면 반복 요일과 공휴일 설정에 관계없이 울립니다. 다만 ‘다음 한 번 건너뛰기’를 선택한 알람은 건너뜁니다."
            else "출처: holidays-kr · Hyunbin Seo\n월력요항 기반 공개 자료\n\n새 임시공휴일은 제공처에 반영된 뒤 업데이트됩니다.") },
        confirmButton = { TextButton(onClick = { help = false }) { Text("닫기") } })
}
