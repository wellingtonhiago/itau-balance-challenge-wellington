package br.com.itau.challenge.balance.adapter.input.web.dto

import br.com.itau.challenge.balance.domain.model.AccountBalance
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class AccountBalanceResponse(
    val id: UUID,
    val owner: UUID,
    val balance: BalanceResponse,
    @JsonProperty("updated_at") val updatedAt: String,
) {
    data class BalanceResponse(val amount: BigDecimal, val currency: String)

    companion object {
        fun from(accountBalance: AccountBalance, zoneId: ZoneId) = AccountBalanceResponse(
            id = accountBalance.accountId,
            owner = accountBalance.ownerId,
            balance = BalanceResponse(
                amount = accountBalance.balance.amount,
                currency = accountBalance.balance.currency.currencyCode,
            ),
            updatedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                accountBalance.updatedAt.toInstant().atZone(zoneId),
            ),
        )
    }
}