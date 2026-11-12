package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.adapter.input.kafka.dto.TransactionProcessedMessage
import br.com.itau.challenge.balance.domain.model.Account
import br.com.itau.challenge.balance.domain.model.AccountStatus
import br.com.itau.challenge.balance.domain.model.CanonicalUuid
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.domain.model.Transaction
import br.com.itau.challenge.balance.domain.model.TransactionEvent
import br.com.itau.challenge.balance.domain.model.TransactionStatus
import br.com.itau.challenge.balance.domain.model.TransactionType
import java.util.UUID

internal object TransactionProcessedMessageMapper {

    fun toDomain(message: TransactionProcessedMessage): TransactionEvent = TransactionEvent(
        transaction = with(message.transaction) {
            Transaction(
                id = uuid(id, "transaction.id"),
                type = TransactionType.from(type),
                amount = Money.of(amount, currency),
                status = TransactionStatus.from(status),
                timestamp = EventTimestamp(timestamp),
            )
        },
        account = with(message.account) {
            Account(
                id = uuid(id, "account.id"),
                ownerId = uuid(owner, "account.owner"),
                status = AccountStatus(status),
                balance = Money.of(balance.amount, balance.currency),
            )
        },
    )

    private fun uuid(value: String, field: String): UUID =
        CanonicalUuid.parseOrNull(value)
            ?: throw InvalidTransactionMessageException("Field '$field' must be a canonical UUID, got '$value'")
}