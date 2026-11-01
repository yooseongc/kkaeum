package com.kkaeum.alarm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Shared by the ringing activity and silent previews; no audio or scheduling dependencies. */
@Composable fun MissionPanel(alarm: Alarm, snapshot: String, onAction: (MissionAction) -> Unit) {
    val session = remember(alarm.missionType, snapshot) { MissionFactory.create(alarm, snapshot) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${MissionTypes.title(alarm.missionType)} · ${session.completed + 1} / ${session.total}", style = MaterialTheme.typography.titleLarge)
        if (session is SumSession) SumPanel(session) { onAction(MissionAction.Answer(it)) }
        else if (session is TouchMission) TouchPanel(session) { onAction(MissionAction.Select(it)) }
    }
}

@Composable private fun SumPanel(session: SumSession, submit: (Int?) -> Unit) {
    var answer by rememberSaveable(session.snapshot()) { mutableStateOf("") }
    Text("${session.math.problem.left} + ${session.math.problem.right}", style = MaterialTheme.typography.displayMedium)
    Text(answer.ifEmpty { "답을 입력하세요" }, style = MaterialTheme.typography.headlineSmall)
    // Input events also reach the host so its inactivity timer can be reset.
    val interact = LocalMissionInteraction.current
    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("지움", "0", "확인")).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { key ->
                Button(onClick = {
                    interact()
                    when (key) {
                        "확인" -> { submit(answer.toIntOrNull()); answer = "" }
                        "지움" -> answer = answer.dropLast(1)
                        else -> if (answer.length < 3) answer += key
                    }
                }, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text(key, fontSize = 26.sp) }
            }
        }
    }
}

val LocalMissionInteraction = staticCompositionLocalOf<() -> Unit> { {} }

@Composable private fun TouchPanel(m: TouchMission, select: (Int) -> Unit) {
    var lit by remember(m.seed, m.completed) { mutableIntStateOf(-1) }
    val selectLatest by rememberUpdatedState(select)
    LaunchedEffect(m.seed, m.completed, m.revealed) {
        if (m.revealed) {
            if (m.type == "sequence") {
                delay(600)
                for (cell in m.solution) { lit = cell; delay(800); lit = -1; delay(350) }
            } else delay(2500L + m.level * 500)
            selectLatest(TouchInput.HIDE_REVEAL)
        }
    }
    Text(if (m.revealed && m.type == "sequence") "켜지는 순서를 기억하세요"
        else if (m.revealed && m.type == "memory") "켜진 칸을 기억하세요" else m.prompt,
        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    if (m.type == "sequence" && !m.revealed) Text("${m.step} / ${m.solution.size} 입력")
    if (m.type == "order") Text("다음 숫자: ${m.step + 1}")
    if (m.type == "slide") {
        var reference by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { reference = true; selectLatest(TouchInput.INTERACT) }) { Text("완성 그림 보기") }
        if (reference) AlertDialog(onDismissRequest = { reference = false }, title = { Text("완성 그림") },
            text = { PictureTile(0, Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "산과 해가 있는 풍경" }) },
            confirmButton = { TextButton(onClick = { reference = false }) { Text("닫기") } })
    }
    val maxBoard = if (m.type == "slide") 420.dp else 620.dp
    Column(Modifier.widthIn(max = maxBoard).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        m.labels.indices.toList().chunked(m.columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { index ->
                    val matched = index in m.matched
                    val highlighted = index == m.picked || (m.revealed && ((m.type == "memory" && index in m.solution) || (m.type == "sequence" && index == lit)))
                    val isHidden = m.type == "pairs" && !matched && index != m.picked
                    val label = when {
                        m.type == "memory" -> if (matched || highlighted) "●" else ""
                        isHidden -> "?"
                        matched -> "✓ ${m.labels[index]}"
                        else -> m.labels[index]
                    }
                    if (m.type == "slide") {
                        val tile = m.board[index]
                        var drag by remember { mutableStateOf(Offset.Zero) }
                        Surface(onClick = { select(index) }, enabled = tile != 0, shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f).aspectRatio(1f).semantics { contentDescription = if (tile == 0) "빈칸" else "조각 $tile" }
                                .pointerInput(m.snapshot(), index) {
                                    detectDragGestures(onDragStart = { drag = Offset.Zero }, onDrag = { change, amount -> change.consume(); drag += amount },
                                        onDragEnd = {
                                            val blank = m.board.indexOf(0)
                                            val dx = blank % 3 - index % 3; val dy = blank / 3 - index / 3
                                            if (TouchMission.adjacent(index, blank) && drag.x * dx + drag.y * dy > 24.dp.toPx()) selectLatest(index)
                                        })
                                }) {
                            if (tile != 0) Box {
                                PictureTile(tile, Modifier.fillMaxSize())
                                Surface(color = Color(0xDDFFFFFF), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(5.dp)) {
                                    Text("$tile", color = Color.Black, modifier = Modifier.padding(horizontal = 6.dp), fontSize = 18.sp)
                                }
                            }
                        }
                    } else Button(onClick = { select(index) }, enabled = !m.revealed && !matched,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 80.dp).semantics {
                            contentDescription = if (m.type == "memory") "${index + 1}번 칸${if (matched) " 완료" else ""}" else label
                        }) { Text(label, fontSize = if (m.type == "connect" || label.length > 4) 22.sp else 32.sp, textAlign = TextAlign.Center) }
                }
                repeat(m.columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (m.type in listOf("memory", "sequence") && !m.revealed) TextButton(onClick = { select(TouchInput.SHOW_AGAIN) }) { Text("다시 보기") }
}

/** One local vector scene, cropped into eight tiles. No network or credential-protected assets. */
@Composable private fun PictureTile(tile: Int, modifier: Modifier) {
    Canvas(modifier) {
        val scale = if (tile == 0) 1f else 3f
        val w = size.width * scale; val h = size.height * scale
        val column = if (tile == 0) 0 else (tile - 1) % 3
        val row = if (tile == 0) 0 else (tile - 1) / 3
        clipRect { translate(-column * size.width, -row * size.height) {
            drawRect(Color(0xFF9CD8F3), size = Size(w, h))
            drawCircle(Color(0xFFFFCF4B), w * .13f, Offset(w * .75f, h * .22f))
            drawPath(Path().apply { moveTo(0f,h*.75f); lineTo(w*.35f,h*.25f); lineTo(w*.75f,h*.8f); close() }, Color(0xFF4F8666))
            drawPath(Path().apply { moveTo(w*.4f,h*.8f); lineTo(w*.8f,h*.4f); lineTo(w,h*.65f); lineTo(w,h); close() }, Color(0xFF2D654B))
            drawRect(Color(0xFF448EB2), topLeft = Offset(0f,h*.8f), size = Size(w,h*.2f))
            drawLine(Color.White, Offset(w*.1f,h*.9f), Offset(w*.55f,h*.9f), h*.02f)
        } }
    }
}

@Composable fun MissionPreview(alarm: Alarm, close: () -> Unit) {
    var snapshot by rememberSaveable { mutableStateOf(MissionFactory.create(alarm).snapshot()) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("미리 풀어보기", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = close) { Text("닫기") }
                }
                if (finished) {
                    Text("미션 완료", style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = { snapshot = MissionFactory.create(alarm).snapshot(); finished = false; message = "" }) { Text("다시 풀기") }
                } else Box(Modifier.widthIn(max = 720.dp)) {
                    MissionPanel(alarm, snapshot) { action ->
                        val session = MissionFactory.create(alarm, snapshot)
                        session.accept(action); message = session.message
                        finished = session.finished; snapshot = session.snapshot()
                    }
                }
                if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
