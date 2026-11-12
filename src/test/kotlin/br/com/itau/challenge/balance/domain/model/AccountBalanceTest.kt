package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.model.BalanceFixtures.TIMESTAMP_MICROS
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.accountBalance
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountBalanceTest {

    private val lowerTransactionId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val higherTransactionId = UUID.fromString("ffffffff-0000-0000-0000-000000000001")

    @Test
    fun `should be newer when its timestamp is later`() {
        val current = accountBalance(timestampMicros = TIMESTAMP_MICROS)
        val incoming = accountBalance(timestampMicros = TIMESTAMP_MICROS + 1)

        assertTrue(incoming.isNewerThan(current))
        assertFalse(current.isNewerThan(incoming))
    }

    @Test
    fun `should not be newer than an identical balance so duplicates are ignored`() {
        assertFalse(accountBalance().isNewerThan(accountBalance()))
    }

    @Test
    fun `should break timestamp ties by the textual transaction id`() {
        val lower = accountBalance(transactionId = lowerTransactionId)
        val higher = accountBalance(transactionId = higherTransactionId)

        assertTrue(higher.isNewerThan(lower))
        assertFalse(lower.isNewerThan(higher))
    }

    @Test
    fun `should reject comparing balances of different accounts`() {
        assertFailsWith<IllegalArgumentException> {
            accountBalance().isNewerThan(accountBalance(accountId = UUID.randomUUID()))
        }
    }
}