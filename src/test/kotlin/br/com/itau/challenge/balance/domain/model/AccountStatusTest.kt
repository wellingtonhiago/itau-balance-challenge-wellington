package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountStatusTest {

    @Test
    fun `should keep any non blank status as received`() {
        assertEquals("BLOCKED", AccountStatus("BLOCKED").value)
    }

    @Test
    fun `should reject a blank status`() {
        val exception = assertFailsWith<DomainValidationException> { AccountStatus(" ") }

        assertEquals("Account status must not be blank", exception.message)
    }
}