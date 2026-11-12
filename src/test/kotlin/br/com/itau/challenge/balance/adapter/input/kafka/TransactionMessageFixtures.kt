package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.BalanceFixtures

object TransactionMessageFixtures {

    fun json(
        transactionId: String = BalanceFixtures.TRANSACTION_ID.toString(),
        accountId: String = BalanceFixtures.ACCOUNT_ID.toString(),
        ownerId: String = BalanceFixtures.OWNER_ID.toString(),
        type: String = "CREDIT",
        amount: String = "97.07",
        currency: String = "BRL",
        status: String = "APPROVED",
        timestamp: String = BalanceFixtures.TIMESTAMP_MICROS.toString(),
        accountStatus: String = "ENABLED",
        balanceAmount: String = "183.12",
    ): String =
        """
        {
          "transaction": {
            "id": "$transactionId",
            "type": "$type",
            "amount": $amount,
            "currency": "$currency",
            "status": "$status",
            "timestamp": $timestamp
          },
          "account": {
            "id": "$accountId",
            "owner": "$ownerId",
            "created_at": "1634874339",
            "status": "$accountStatus",
            "balance": {
              "amount": $balanceAmount,
              "currency": "BRL"
            }
          }
        }
        """.trimIndent()
}