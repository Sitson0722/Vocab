package com.sitson.vocab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraduationPolicyTest {
    private val strong = List(3) { DimensionEvidence(0.9, 35.0) }

    @Test fun `level requires both mastery and stable time`() {
        assertEquals(5, GraduationPolicy.level(0.9, 35.0))
        assertEquals(4, GraduationPolicy.level(0.9, 20.0))
        assertEquals(2, GraduationPolicy.level(0.4, 20.0))
    }

    @Test fun `graduation requires age all dimensions and diverse materials`() {
        assertTrue(GraduationPolicy.isMastered(90.0, strong, 3))
        assertFalse(GraduationPolicy.isMastered(89.0, strong, 3))
        assertFalse(GraduationPolicy.isMastered(120.0, strong.take(2), 3))
        assertFalse(GraduationPolicy.isMastered(120.0, strong, 2))
    }
}
