package com.kkaeum.alarm

/** The alarm lifecycle depends on this contract, not on an individual puzzle. */
sealed interface MissionAction {
    data class Answer(val value: Int?) : MissionAction
    data class Select(val index: Int) : MissionAction
}

interface MissionSession {
    val total: Int
    val completed: Int
    val finished: Boolean
    val message: String
    fun accept(action: MissionAction): Boolean
    fun snapshot(): String
}

class SumSession(override val total: Int, digits: Int) : MissionSession {
    val math = MathMission(total, digits)
    override val completed get() = math.completed
    override val finished get() = math.finished
    override var message = ""; private set
    override fun accept(action: MissionAction): Boolean {
        if (action !is MissionAction.Answer) return false
        val correct = action.value?.let { math.submit(it) } == true
        message = if (correct) "" else "다시 계산해 보세요"
        return correct
    }
    override fun snapshot() = "sum|$completed|${math.problem.left}|${math.problem.right}"
}

object MissionFactory {
    fun create(alarm: Alarm, snapshot: String = ""): MissionSession {
        if (alarm.missionType == "math") return SumSession(alarm.count, alarm.digits).also { session ->
            if (snapshot.isNotEmpty()) runCatching {
                val fields = snapshot.split('|'); require(fields.size == 4 && fields[0] == "sum")
                session.math.restore(fields[1].toInt(), fields[2].toInt(), fields[3].toInt())
            }
        }
        if (snapshot.isNotEmpty()) runCatching {
            TouchMission.restore(alarm.missionType, alarm.count, alarm.missionLevel, alarm.choiceKind, snapshot)
        }.getOrNull()?.let { if (!it.finished) return it }
        return TouchMission(alarm.missionType, alarm.count, alarm.missionLevel, alarm.choiceKind)
    }
}
