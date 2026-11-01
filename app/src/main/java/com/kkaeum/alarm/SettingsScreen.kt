package com.kkaeum.alarm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(options: AppOptions, appearance: (AppOptions) -> Unit,
    manage: (String) -> Unit, calendar: () -> Unit, check: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = back) { Text("← 알람 목록") }
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingCard("알람") {
                OutlinedButton(onClick = check, shape = MaterialTheme.shapes.medium) { Text("알람 점검") }
            }
            SettingCard("알람 관리") {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("groups" to "알람 그룹", "periods" to "기간 건너뛰기", "backup" to "백업 · 복원", "history" to "울림 기록").forEach { (mode,title) ->
                        OutlinedButton(onClick={manage(mode)},shape=MaterialTheme.shapes.medium) { Text(title) }
                    }
                }
            }
            SettingCard("화면 테마") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "기기 설정 따름", "light" to "라이트", "dark" to "다크").forEach { (value, label) ->
                        FilterChip(options.theme == value, { appearance(options.copy(theme = value)) }, label = { Text(label) })
                    }
                }
            }
            SettingCard("시계") {
                ToggleRow("24시간 형식", null, options.clock24) { appearance(options.copy(clock24 = it)) }
                ToggleRow("초 표시", null, options.seconds) { appearance(options.copy(seconds = it)) }
            }
            SettingCard("공휴일") {
                OutlinedButton(onClick = calendar, shape = MaterialTheme.shapes.medium) { Text("공휴일 · 날짜별 설정") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
