package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.model.BalanceFixtures.ACCOUNT_ID
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.OWNER_ID
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.TIMESTAMP_MICROS
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.TRANSACTION_ID
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.account
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.brl
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionEventTest {

    @Test
    fun `should derive the account balance snapshot from the event`() {
        val event = TransactionEvent(transaction(), account(status = "BLOCKED", balance = brl("183.12")))

        val expected = AccountBalance(
            accountId = ACCOUNT_ID,
            ownerId = OWNER_ID,
            balance = brl("183.12"),
            status = AccountStatus("BLOCKED"),
            updatedAt = EventTimestamp(TIMESTAMP_MICROS),
            lastTransactionId = TRANSACTION_ID,
        )
        assertEquals(expected, event.toAccountBalance())
    }

    @Test
    fun `should derive the balance from declined transactions too`() {
        val event = TransactionEvent(transaction(status = TransactionStatus.DECLINED), account())

        assertEquals(brl("183.12"), event.toAccountBalance().balance)
    }
}