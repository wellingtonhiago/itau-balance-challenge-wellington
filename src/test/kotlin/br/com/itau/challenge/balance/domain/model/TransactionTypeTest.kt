package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionTypeTest {

    @Test
    fun `should parse known types`() {
        assertEquals(TransactionType.CREDIT, TransactionType.from("CREDIT"))
        assertEquals(TransactionType.DEBIT, TransactionType.from("DEBIT"))
    }

    @Test
    fun `should reject an unknown type`() {
        val exception = assertFailsWith<DomainValidationException> { TransactionType.from("REFUND") }

        assertEquals("Unknown transaction type 'REFUND'. Expected one of CREDIT, DEBIT", exception.message)
    }
}