package br.com.itau.challenge.balance.builder

import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.domain.model.AccountStatus
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import java.math.BigDecimal
import java.util.UUID

object DomainObjectsBuilder {

    fun accountBalance(
        accountId: UUID = BalanceFixtures.ACCOUNT_ID,
        ownerId: UUID = BalanceFixtures.OWNER_ID,
        amount: String = "183.12",
        status: String = "ENABLED",
        timestampMicros: Long = BalanceFixtures.TIMESTAMP_MICROS,
        transactionId: UUID = UUID.randomUUID(),
    ) = AccountBalance(
        accountId = accountId,
        ownerId = ownerId,
        balance = Money.of(BigDecimal(amount), "BRL"),
        status = AccountStatus(status),
        updatedAt = EventTimestamp(timestampMicros),
        lastTransactionId = transactionId,
    )
}