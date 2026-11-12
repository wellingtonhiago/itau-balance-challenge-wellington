package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.BalanceFixtures.accountBalance
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBalanceItemMapperTest {

    @Test
    fun `should map every attribute of the balance to the item`() {
        val item = AccountBalanceItemMapper.toItem(accountBalance())

        assertEquals("5b19c8b6-0cc4-4c72-a989-0c2ee15fa975", item.getValue("account_id").s())
        assertEquals("315e3cfe-f4af-4cd2-b298-a449e614349a", item.getValue("owner_id").s())
        assertEquals("183.12", item.getValue("balance_amount").n())
        assertEquals("BRL", item.getValue("currency").s())
        assertEquals("ENABLED", item.getValue("account_status").s())
        assertEquals("1751641364589998", item.getValue("updated_at").n())
        assertEquals("8e8ae808-b154-48b5-9f3e-553935cc4543", item.getValue("last_transaction_id").s())
    }

    @Test
    fun `should read back the same balance it wrote`() {
        val balance = accountBalance()

        assertEquals(balance, AccountBalanceItemMapper.toDomain(AccountBalanceItemMapper.toItem(balance)))
    }
}