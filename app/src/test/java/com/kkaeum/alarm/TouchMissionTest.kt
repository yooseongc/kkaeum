package com.kkaeum.alarm

import org.junit.Assert.*
import org.junit.Test

class TouchMissionTest {
    private fun recovered(m: TouchMission) = TouchMission.restore(m.type, m.total, m.level, m.choice, m.snapshot())
    private fun path(board: List<Int>): List<Int> {
        val queue = ArrayDeque<Pair<List<Int>, List<Int>>>()
        queue.add(board to emptyList()); val visited = mutableSetOf(board)
        while (queue.isNotEmpty()) {
            val (current, moves) = queue.removeFirst()
            if (current == (1..8).toList() + 0) return moves
            val blank = current.indexOf(0)
            current.indices.filter { TouchMission.adjacent(it, blank) }.forEach { cell ->
                val next = current.toMutableList().apply { this[blank] = this[cell]; this[cell] = 0 }.toList()
                if (visited.add(next)) queue.add(next to moves + cell)
            }
        }
        error("Unsolvable board")
    }
    @Test fun everyMissionCompletesAcrossRestorationAndMultipleRounds() {
        for (type in MissionTypes.names.keys - "math") for (level in 1..3) {
            var m = TouchMission(type, 3, level, seed = 123)
            repeat(3) { round ->
                if (m.revealed) m.tap(-1)
                val actions = when (type) {
                    "pairs" -> m.labels.indices.groupBy { m.labels[it] }.values.flatten()
                    "connect" -> m.solution.flatMapIndexed { i, right -> listOf(i * 2, right) }
                    "slide" -> path(m.board)
                    else -> m.solution.toList()
                }
                for (action in actions) {
                    assertTrue("$type action=$action", m.tap(action))
                    if (!m.finished) {
                        val saved = m.snapshot(); m = recovered(m); assertEquals(saved, m.snapshot())
                    }
                }
                assertEquals("$type round", round + 1, m.completed)
            }
            assertTrue(m.finished); assertFalse(m.tap(0))
        }
    }
    @Test fun slideBoardsAreShortSolvableAndNeverAlreadyComplete() {
        for (seed in 0L..30L) for (level in 1..3) {
            val m = TouchMission("slide", 1, level, seed = seed)
            assertNotEquals((1..8).toList() + 0, m.board)
            assertTrue(path(m.board).size <= 3 + level * 3)
        }
    }
    @Test fun wrongAnswersAndRepeatedTapsNeverAdvance() {
        for (type in listOf("odd", "choice", "order", "sequence", "memory")) {
            val m = TouchMission(type, 2, seed = 42)
            if (m.revealed) { assertFalse(m.tap(m.solution[0])); m.tap(-1) }
            val wrong = m.labels.indices.first { it !in m.solution.take(if (type == "memory") m.solution.size else 1) }
            assertFalse(m.tap(wrong)); assertEquals(0, m.completed); assertEquals(0, m.step)
        }
        val m = TouchMission("memory", 1, seed = 42); m.tap(-1); m.tap(m.solution[0])
        assertFalse(m.tap(m.solution[0])); assertEquals(0, m.completed)
    }
    @Test fun choicesHaveUniqueOptionsAndExactlyOneAnswer() {
        for (kind in MissionTypes.choices.keys) for (seed in 0L..100L) {
            val m = TouchMission("choice", 1, 2, kind, seed)
            assertTrue(m.labels.size in 3..4); assertEquals(m.labels.size, m.labels.distinct().size)
            assertEquals(1, m.solution.size); assertTrue(m.tap(m.solution.single())); assertTrue(m.finished)
        }
    }
    @Test fun repeatRevealResetsOnlyCurrentQuestionProgress() {
        val m = TouchMission("sequence", 2, seed = 1); m.tap(-1); m.tap(m.solution.first())
        assertEquals(1, m.step); m.tap(-2); assertEquals(0, m.step); assertTrue(m.revealed)
        val restored = recovered(m); assertTrue(restored.revealed); assertEquals(m.solution, restored.solution)
    }
    @Test fun invalidSnapshotIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TouchMission.restore("slide",1,1,"mixed","1|5|0|0|-1|0|0,0,0,0,0,0,0,0,0|") }
    }
    @Test fun mathUsesSameSessionContractAndPreservesPartialProgress() {
        val alarm = Alarm(count = 3, digits = 2)
        val session = MissionFactory.create(alarm) as SumSession
        assertTrue(session.accept(MissionAction.Answer(session.math.problem.answer)))
        val restored = MissionFactory.create(alarm, session.snapshot()) as SumSession
        assertEquals(1, restored.completed); assertEquals(session.math.problem, restored.math.problem)
        assertFalse(restored.accept(MissionAction.Select(0))); assertFalse(restored.finished)
    }
    @Test fun badSnapshotsRestartQuestionInsteadOfDismissingAlarm() {
        for (type in MissionTypes.names.keys) {
            val session = MissionFactory.create(Alarm(missionType = type), "not-a-snapshot")
            assertEquals(0, session.completed); assertFalse(session.finished)
        }
    }
}
