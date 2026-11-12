package br.com.itau.challenge.balance.domain.model

import java.util.UUID

data class AccountBalance(
    val accountId: UUID,
    val ownerId: UUID,
    val balance: Money,
    val status: AccountStatus,
    val updatedAt: EventTimestamp,
    val lastTransactionId: UUID,
) {
    /**
     * Ties on [updatedAt] are broken by the textual [lastTransactionId], the same ordering
     * DynamoDB applies to string attributes in conditional writes.
     */
    fun isNewerThan(other: AccountBalance): Boolean {
        require(accountId == other.accountId) { "Cannot compare balances of different accounts" }
        return compareValuesBy(this, other, { it.updatedAt }, { it.lastTransactionId.toString() }) > 0
    }
}