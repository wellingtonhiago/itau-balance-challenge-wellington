package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.brl
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionTest {

    @Test
    fun `should accept a positive amount`() {
        assertEquals(brl("0.01"), transaction(amount = brl("0.01")).amount)
    }

    @Test
    fun `should reject a zero amount`() {
        val exception = assertFailsWith<DomainValidationException> { transaction(amount = brl("0.00")) }

        assertEquals("Transaction amount must be positive, got 0.00", exception.message)
    }

    @Test
    fun `should reject a negative amount`() {
        assertFailsWith<DomainValidationException> { transaction(amount = brl("-1")) }
    }
}