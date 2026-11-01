package com.kkaeum.alarm

import kotlin.random.Random

/** Pure state machine. Generated questions depend only on seed and round; snapshots include partial answers. */
class TouchMission(val type: String, override val total: Int, val level: Int = 1, val choice: String = "mixed",
    val seed: Long = Random.nextLong()) : MissionSession {
    init { require(type in MissionTypes.names && type != "math"); require(total in 1..10 && level in 1..3); require(choice in MissionTypes.choices) }
    override var completed = 0; private set
    var step = 0; private set
    var picked = -1; private set
    var revealed = false; private set
    var board = emptyList<Int>(); private set
    var matched = emptySet<Int>(); private set
    var labels = emptyList<String>(); private set
    var solution = emptyList<Int>(); private set
    var prompt = ""; private set
    override var message = ""; private set
    var columns = 3; private set
    override val finished get() = completed >= total
    override fun accept(action: MissionAction) = if (action is MissionAction.Select) tap(action.index) else false
    init { generate() }

    private fun generate() {
        val question = PuzzleGenerators.create(type, level, choice, seed + completed * 104729L)
        step = 0; picked = -1; matched = emptySet()
        revealed = type in listOf("memory", "sequence"); message = ""
        columns = question.columns; board = question.board; labels = question.labels
        solution = question.solution; prompt = question.prompt
    }

    fun tap(index: Int): Boolean {
        if (finished) return false
        message = ""
        if (index == TouchInput.HIDE_REVEAL && revealed) { revealed = false; prompt = if (type == "memory") "기억한 칸을 누르세요" else "같은 순서로 누르세요"; return true }
        if (index == TouchInput.SHOW_AGAIN && type in listOf("memory", "sequence")) { revealed = true; step = 0; matched = emptySet(); return true }
        if (index !in labels.indices || revealed) return false
        fun wrong(): Boolean { message = "다시 골라보세요"; return false }
        var done = false
        when (type) {
            "odd", "choice" -> { if (index != solution[0]) return wrong(); done = true }
            "order", "sequence" -> {
                if (type == "order" && index in matched) return false
                if (index != solution[step]) return wrong()
                step++; if (type == "order") matched = matched + index
                done = step == solution.size
            }
            "memory" -> {
                if (index in matched) return false
                if (index !in solution) return wrong()
                matched = matched + index; done = matched.size == solution.size
            }
            "pairs" -> {
                if (index in matched || index == picked) return false
                if (picked == -1) picked = index
                else if (labels[index] == labels[picked]) { matched = matched + index + picked; picked = -1 }
                else { picked = index; return wrong() }
                done = matched.size == labels.size
            }
            "connect" -> {
                if (index in matched) return false
                if (index % 2 == 0) picked = index
                else {
                    if (picked < 0) { message = "왼쪽 항목을 먼저 누르세요"; return false }
                    if (solution[picked / 2] != index) return wrong()
                    matched = matched + index + picked; picked = -1
                }
                done = matched.size == labels.size
            }
            "slide" -> {
                val blank = board.indexOf(0)
                if (!adjacent(index, blank)) return false
                board = board.toMutableList().apply { this[blank] = this[index]; this[index] = 0 }
                done = board == (1..8).toList() + 0
            }
        }
        if (done) { completed++; if (!finished) generate() }
        return true
    }

    override fun snapshot(): String = listOf("1", seed, completed, step, picked, if (revealed) 1 else 0,
        board.joinToString(","), matched.sorted().joinToString(",")).joinToString("|")

    companion object {
        fun adjacent(a: Int, b: Int) = kotlin.math.abs(a / 3 - b / 3) + kotlin.math.abs(a % 3 - b % 3) == 1
        fun restore(type: String, total: Int, level: Int, choice: String, text: String): TouchMission {
            require(text.length < 2048)
            val p = text.split('|'); require(p.size == 8 && p[0] == "1")
            val m = TouchMission(type, total, level, choice, p[1].toLong())
            m.completed = p[2].toInt(); require(m.completed in 0..total); m.generate()
            m.step = p[3].toInt(); m.picked = p[4].toInt(); m.revealed = p[5] == "1"
            require(m.step in 0..m.solution.size && m.picked in -1 until m.labels.size)
            if (type in listOf("order", "sequence") && !m.finished) require(m.step < m.solution.size)
            if (type == "slide") {
                m.board = p[6].split(',').map { it.toInt() }; require(m.board.sorted() == (0..8).toList())
            }
            m.matched = if (p[7].isEmpty()) emptySet() else p[7].split(',').map { it.toInt() }.toSet()
            require(m.matched.all { it in m.labels.indices })
            if (!m.revealed && type in listOf("memory", "sequence")) m.prompt = if (type == "memory") "기억한 칸을 누르세요" else "같은 순서로 누르세요"
            return m
        }
    }
}
