package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.domain.model.TransactionEvent
import br.com.itau.challenge.balance.port.output.SaveAccountBalanceDataProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateAccountBalanceServiceTest {

    private val event = TransactionEvent(BalanceFixtures.transaction(), BalanceFixtures.account())

    @Test
    fun `should save the balance carried by the event and return the outcome`() {
        BalanceUpdateOutcome.entries.forEach { outcome ->
            val saved = mutableListOf<AccountBalance>()
            val service = UpdateAccountBalanceService(SaveAccountBalanceDataProvider { saved += it; outcome })

            assertEquals(outcome, service.updateBalance(event))
            assertEquals(listOf(event.toAccountBalance()), saved)
        }
    }
}