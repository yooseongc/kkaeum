package com.kkaeum.alarm

import org.junit.Assert.*
import org.junit.Test

class SnoozePolicyTest {
    @Test fun finiteLimitStopsExactlyAfterTenSnoozes() {
        var used = 0
        repeat(10) {
            assertTrue(SnoozePolicy.canSnooze(10, used))
            used = SnoozePolicy.nextUsed(used)
        }
        assertFalse(SnoozePolicy.canSnooze(10, used))
        assertFalse(SnoozePolicy.canSnooze(0, 0))
        assertTrue(SnoozePolicy.canSnooze(3, 2))
        assertFalse(SnoozePolicy.canSnooze(3, 3))
    }
    @Test fun unlimitedRemainsAvailableAndCounterDoesNotOverflow() {
        listOf(0, 3, 10, 100, Int.MAX_VALUE).forEach {
            assertTrue(SnoozePolicy.canSnooze(SnoozePolicy.UNLIMITED, it))
        }
        assertEquals(Int.MAX_VALUE, SnoozePolicy.nextUsed(Int.MAX_VALUE))
    }
    @Test fun backupLimitsAcceptOnlySupportedValues() {
        (-1..10).forEach { assertTrue(SnoozePolicy.isValid(it)) }
        listOf(-2, 11, Int.MAX_VALUE).forEach {
            assertFalse(SnoozePolicy.isValid(it))
            assertFalse(SnoozePolicy.canSnooze(it, 0))
        }
    }
}
