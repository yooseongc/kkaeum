package com.kkaeum.alarm

object SnoozePolicy {
    const val UNLIMITED = -1
    const val MAX_LIMIT = 10
    fun isValid(limit: Int) = limit == UNLIMITED || limit in 0..MAX_LIMIT
    fun canSnooze(limit: Int, used: Int) = limit == UNLIMITED || (limit in 1..MAX_LIMIT && used < limit)
    fun nextUsed(used: Int) = if (used < Int.MAX_VALUE) used + 1 else Int.MAX_VALUE
}
