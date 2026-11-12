package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.domain.model.AccountStatus
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.math.BigDecimal
import java.util.UUID

internal object AccountBalanceItemMapper {
    const val ACCOUNT_ID = "account_id"
    const val OWNER_ID = "owner_id"
    const val BALANCE_AMOUNT = "balance_amount"
    const val CURRENCY = "currency"
    const val ACCOUNT_STATUS = "account_status"
    const val UPDATED_AT = "updated_at"
    const val LAST_TRANSACTION_ID = "last_transaction_id"

    fun key(accountId: UUID): Map<String, AttributeValue> = mapOf(ACCOUNT_ID to string(accountId.toString()))

    fun toItem(balance: AccountBalance): Map<String, AttributeValue> = mapOf(
        ACCOUNT_ID to string(balance.accountId.toString()),
        OWNER_ID to string(balance.ownerId.toString()),
        BALANCE_AMOUNT to number(balance.balance.amount.toPlainString()),
        CURRENCY to string(balance.balance.currency.currencyCode),
        ACCOUNT_STATUS to string(balance.status.value),
        UPDATED_AT to number(balance.updatedAt.epochMicros.toString()),
        LAST_TRANSACTION_ID to string(balance.lastTransactionId.toString()),
    )

    fun toDomain(item: Map<String, AttributeValue>): AccountBalance = AccountBalance(
        accountId = UUID.fromString(item.string(ACCOUNT_ID)),
        ownerId = UUID.fromString(item.string(OWNER_ID)),
        balance = Money.of(BigDecimal(item.number(BALANCE_AMOUNT)), item.string(CURRENCY)),
        status = AccountStatus(item.string(ACCOUNT_STATUS)),
        updatedAt = EventTimestamp(item.number(UPDATED_AT).toLong()),
        lastTransactionId = UUID.fromString(item.string(LAST_TRANSACTION_ID)),
    )

    fun string(value: String): AttributeValue = AttributeValue.builder().s(value).build()

    fun number(value: String): AttributeValue = AttributeValue.builder().n(value).build()

    private fun Map<String, AttributeValue>.string(name: String): String = getValue(name).s()

    private fun Map<String, AttributeValue>.number(name: String): String = getValue(name).n()
}