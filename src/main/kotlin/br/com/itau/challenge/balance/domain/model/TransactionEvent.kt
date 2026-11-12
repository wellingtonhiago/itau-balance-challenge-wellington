package br.com.itau.challenge.balance.domain.model

data class TransactionEvent(val transaction: Transaction, val account: Account) {

    fun toAccountBalance(): AccountBalance = AccountBalance(
        accountId = account.id,
        ownerId = account.ownerId,
        balance = account.balance,
        status = account.status,
        updatedAt = transaction.timestamp,
        lastTransactionId = transaction.id,
    )
}