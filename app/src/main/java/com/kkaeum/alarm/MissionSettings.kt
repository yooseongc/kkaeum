package com.kkaeum.alarm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable fun MissionSettings(alarm: Alarm, changed: (Alarm) -> Unit, preview: () -> Unit) {
    SettingCard("미션") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MissionTypes.names.forEach { (type, title) ->
                FilterChip(alarm.missionType == type, { changed(alarm.copy(missionType = type)) }, label = { Text(title) })
            }
        }
        if (alarm.missionType == "math") FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..2).forEach { digits -> FilterChip(alarm.digits == digits, { changed(alarm.copy(digits = digits)) }, label = { Text("${digits}자리 덧셈") }) }
        } else if (!(alarm.missionType == "choice" && alarm.choiceKind in listOf("direction", "category"))) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("쉬움", "보통", "어려움").forEachIndexed { index, title ->
                FilterChip(alarm.missionLevel == index + 1, { changed(alarm.copy(missionLevel = index + 1)) }, label = { Text(title) })
            }
        }
        if (alarm.missionType == "choice") FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MissionTypes.choices.forEach { (kind, title) ->
                FilterChip(alarm.choiceKind == kind, { changed(alarm.copy(choiceKind = kind)) }, label = { Text(title) })
            }
        }
        Stepper("문제 수", alarm.count, 1..10, "문제") { changed(alarm.copy(count = it)) }
        OutlinedButton(onClick = preview, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("미리 풀어보기") }
        Text("입력이 없으면 ${alarm.quietSeconds}초 후 다시 울림", color = Muted)
        Slider(alarm.quietSeconds.toFloat(), { changed(alarm.copy(quietSeconds = (it / 5).toInt() * 5)) }, valueRange = 10f..60f, steps = 9)
    }
}
