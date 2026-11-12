package br.com.itau.challenge.balance.domain.exception

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainValidationExceptionTest {

    @Test
    fun `should carry the message it was created with`() {
        assertEquals("invalid", DomainValidationException("invalid").message)
    }
}