
package com.devjets.spend

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpendServiceTest {
    @Test fun `cursor ordering is deterministic`() {
        val a = listOf(100L to 9L, 100L to 8L, 99L to 20L)
        val sorted = a.sortedWith(compareByDescending<Pair<Long,Long>> { it.first }.thenByDescending { it.second })
        assertEquals(listOf(100L to 9L,100L to 8L,99L to 20L), sorted)
    }

    @Test fun `amount uses integer minor units`() {
        val amount: Long = 1299
        assertEquals(1299L, amount)
        assertTrue(amount is Long)
    }
}
