package com.kkaeum.alarm

import kotlin.random.Random

data class SumProblem(val left: Int, val right: Int) { val answer get() = left + right }
class MathMission(val total: Int, val digits: Int, private val random: Random = Random.Default) {
    init { require(total in 1..10); require(digits in 1..2) }
    var completed = 0; private set
    var problem = next(); private set
    val finished get() = completed >= total
    fun submit(answer: Int): Boolean {
        if (finished || answer != problem.answer) return false
        completed++
        if (!finished) problem = next()
        return true
    }
    fun restore(completed: Int, left: Int, right: Int) {
        val range = if (digits == 1) 1..9 else 10..99
        require(completed in 0 until total && left in range && right in range)
        this.completed = completed; problem = SumProblem(left, right)
    }
    private fun next(): SumProblem {
        val range = if (digits == 1) 1..9 else 10..99
        return SumProblem(random.nextInt(range.first, range.last + 1), random.nextInt(range.first, range.last + 1))
    }
}
