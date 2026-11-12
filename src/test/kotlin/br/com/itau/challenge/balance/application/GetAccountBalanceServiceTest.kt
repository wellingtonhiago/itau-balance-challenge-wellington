package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.exception.AccountBalanceNotFoundException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.port.output.FindAccountBalanceDataProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetAccountBalanceServiceTest {

    @Test
    fun `should return the stored balance`() {
        val balance = BalanceFixtures.accountBalance()
        val service = GetAccountBalanceService { id -> balance.takeIf { it.accountId == id } }

        assertEquals(balance, service.getBalance(BalanceFixtures.ACCOUNT_ID))
    }

    @Test
    fun `should fail when the account has no balance`() {
        val service = GetAccountBalanceService(FindAccountBalanceDataProvider { null })

        val exception = assertFailsWith<AccountBalanceNotFoundException> { service.getBalance(BalanceFixtures.ACCOUNT_ID) }

        assertEquals("No balance found for account ${BalanceFixtures.ACCOUNT_ID}", exception.message)
    }
}