package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.ensure
import java.util.UUID

data class Transaction(
    val id: UUID,
    val type: TransactionType,
    val amount: Money,
    val status: TransactionStatus,
    val timestamp: EventTimestamp,
) {
    init {
        ensure(amount.isPositive) { "Transaction amount must be positive, got ${amount.amount}" }
    }
}