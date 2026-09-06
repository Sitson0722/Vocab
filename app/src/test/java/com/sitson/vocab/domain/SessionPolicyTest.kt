package com.sitson.vocab.domain

import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun `time budget and review backlog constrain new learning`() {
        assertFalse(SessionPolicy.canIntroduce(600_000, 550_000, 0, 10))
        assertTrue(SessionPolicy.canIntroduce(600_000, 200_000, 0, 10))
        assertFalse(SessionPolicy.canIntroduce(600_000, 0, 3, 10))
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
