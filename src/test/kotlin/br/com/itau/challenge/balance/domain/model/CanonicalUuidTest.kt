package br.com.itau.challenge.balance.domain.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanonicalUuidTest {

    @Test
    fun `should parse canonical UUIDs regardless of case`() {
        val value = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975"

        assertEquals(UUID.fromString(value), CanonicalUuid.parseOrNull(value))
        assertEquals(UUID.fromString(value), CanonicalUuid.parseOrNull(value.uppercase()))
    }

    @Test
    fun `should reject invalid and non canonical UUIDs`() {
        listOf("", "not-a-uuid", "1-1-1-1-1", "5b19c8b60cc44c72a9890c2ee15fa975").forEach {
            assertNull(CanonicalUuid.parseOrNull(it), "expected '$it' to be rejected")
        }
    }
}