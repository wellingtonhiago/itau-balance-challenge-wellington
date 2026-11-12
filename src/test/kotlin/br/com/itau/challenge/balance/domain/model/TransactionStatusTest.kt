package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionStatusTest {

    @Test
    fun `should parse approved and declined`() {
        assertEquals(TransactionStatus.APPROVED, TransactionStatus.from("APPROVED"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.from("DECLINED"))
    }

    @Test
    fun `should reject an unknown status`() {
        val exception = assertFailsWith<DomainValidationException> { TransactionStatus.from("approved") }

        assertEquals("Unknown transaction status 'approved'. Expected one of APPROVED, DECLINED", exception.message)
    }
}