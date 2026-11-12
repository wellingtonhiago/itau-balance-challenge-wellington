package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventTimestampTest {

    @Test
    fun `should convert epoch microseconds to an instant keeping microsecond precision`() {
        val instant = EventTimestamp(1_751_641_364_589_998L).toInstant()

        assertEquals(Instant.parse("2025-07-04T15:02:44.589998Z"), instant)
    }

    @Test
    fun `should order timestamps chronologically`() {
        assertTrue(EventTimestamp(2) > EventTimestamp(1))
        assertEquals(0, EventTimestamp(1).compareTo(EventTimestamp(1)))
    }

    @Test
    fun `should reject zero`() {
        val exception = assertFailsWith<DomainValidationException> { EventTimestamp(0) }

        assertEquals("Timestamp must be a positive epoch in microseconds, got 0", exception.message)
    }

    @Test
    fun `should reject negative values`() {
        assertFailsWith<DomainValidationException> { EventTimestamp(-1) }
    }
}