package br.com.itau.challenge.balance.builder

import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.junit.readResource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.util.UUID

const val TRANSACTION_PROCESSED_EVENT = "payload/transaction-processed-event.json"
const val INVALID_JSON = "payload/invalid-json.json"

/** Builds `transacoes-financeiras-processadas` payloads from [TRANSACTION_PROCESSED_EVENT], overriding the given fields. */
object PayloadObjectsBuilder {

    private val jsonMapper = JsonMapper.builder().build()

    fun transactionProcessedEvent(
        accountId: UUID = BalanceFixtures.ACCOUNT_ID,
        transactionId: UUID = BalanceFixtures.TRANSACTION_ID,
        timestampMicros: Long = BalanceFixtures.TIMESTAMP_MICROS,
        balance: String = "183.12",
        currency: String = "BRL",
    ): String {
        val event = jsonMapper.readTree(readResource(TRANSACTION_PROCESSED_EVENT)) as ObjectNode
        (event.get("transaction") as ObjectNode)
            .put("id", transactionId.toString())
            .put("currency", currency)
            .put("timestamp", timestampMicros)
        val account = (event.get("account") as ObjectNode).put("id", accountId.toString())
        (account.get("balance") as ObjectNode).put("amount", BigDecimal(balance))
        return jsonMapper.writeValueAsString(event)
    }

    fun invalidJson(): String = readResource(INVALID_JSON)
}