package com.kkaeum.alarm

object MissionTypes {
    val names = linkedMapOf("math" to "수학", "odd" to "다른 모양 찾기", "order" to "숫자 순서 누르기",
        "memory" to "위치 기억하기", "pairs" to "카드 짝 맞추기", "sequence" to "불빛 순서 따라 누르기",
        "connect" to "연관 항목 연결하기", "slide" to "슬라이딩 퍼즐", "choice" to "선택형 퍼즐")
    val choices = linkedMapOf("mixed" to "섞어서", "pattern" to "규칙", "count" to "개수", "direction" to "방향", "category" to "다른 하나")
    fun title(type: String) = names[type] ?: names.getValue("math")
    fun summary(alarm: Alarm) = if (alarm.missionType == "math") "${alarm.digits}자리 덧셈 ${alarm.count}문제"
        else "${title(alarm.missionType)} ${alarm.count}문제"
}

internal object TouchInput {
    const val HIDE_REVEAL = -1
    const val SHOW_AGAIN = -2
    const val INTERACT = -3
}
