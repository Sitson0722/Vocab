package com.sitson.vocab.domain

import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun `small word pools use a longer real delay without an immediate fallback`() {
        assertFalse(SessionPolicy.canRepeat(600_000, 1000, 0))
        assertTrue(SessionPolicy.canRepeat(601_000, 1000, 0))
    }
    @Test fun `same word needs both time and intervening work`() {
        assertFalse(SessionPolicy.canRepeat(121_000, 1000, 2))
        assertFalse(SessionPolicy.canRepeat(61_000, 1000, 3))
        assertTrue(SessionPolicy.canRepeat(121_000, 1000, 3))
    }
    @Test fun `overdue priority is relative to scheduled interval`() {
        val day = SessionPolicy.DAY
        assertTrue(SessionPolicy.overdue(10 * day, 5 * day, 4 * day) > SessionPolicy.overdue(10 * day, 5 * day, 0))
    }
}
