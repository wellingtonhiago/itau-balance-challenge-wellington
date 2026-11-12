package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.adapter.input.kafka.dto.TransactionProcessedMessage
import br.com.itau.challenge.balance.adapter.input.kafka.dto.TransactionProcessedMessage.AccountPayload
import br.com.itau.challenge.balance.adapter.input.kafka.dto.TransactionProcessedMessage.BalancePayload
import br.com.itau.challenge.balance.adapter.input.kafka.dto.TransactionProcessedMessage.TransactionPayload
import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.domain.model.TransactionEvent
import br.com.itau.challenge.balance.domain.model.TransactionStatus
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionProcessedMessageMapperTest {

    private fun message(
        transactionId: String = BalanceFixtures.TRANSACTION_ID.toString(),
        accountId: String = BalanceFixtures.ACCOUNT_ID.toString(),
        ownerId: String = BalanceFixtures.OWNER_ID.toString(),
        type: String = "CREDIT",
        status: String = "APPROVED",
        amount: String = "97.07",
        timestamp: Long = BalanceFixtures.TIMESTAMP_MICROS,
        accountStatus: String = "ENABLED",
    ) = TransactionProcessedMessage(
        transaction = TransactionPayload(transactionId, type, BigDecimal(amount), "BRL", status, timestamp),
        account = AccountPayload(accountId, ownerId, accountStatus, BalancePayload(BigDecimal("183.12"), "BRL")),
    )

    @Test
    fun `should map the message to a transaction event`() {
        val expected = TransactionEvent(BalanceFixtures.transaction(), BalanceFixtures.account())

        assertEquals(expected, TransactionProcessedMessageMapper.toDomain(message()))
    }

    @Test
    fun `should map declined transactions and accounts with any status`() {
        val event = TransactionProcessedMessageMapper.toDomain(message(status = "DECLINED", accountStatus = "BLOCKED"))

        assertEquals(TransactionStatus.DECLINED, event.transaction.status)
        assertEquals("BLOCKED", event.account.status.value)
    }

    @Test
    fun `should accept uppercase UUIDs`() {
        val event = TransactionProcessedMessageMapper.toDomain(
            message(accountId = BalanceFixtures.ACCOUNT_ID.toString().uppercase()),
        )

        assertEquals(BalanceFixtures.ACCOUNT_ID, event.account.id)
    }

    @Test
    fun `should reject invalid or non canonical UUIDs`() {
        listOf(
            message(transactionId = "not-a-uuid"),
            message(accountId = "1-1-1-1-1"),
            message(ownerId = ""),
        ).forEach { message ->
            assertFailsWith<InvalidTransactionMessageException> { TransactionProcessedMessageMapper.toDomain(message) }
        }
    }

    @Test
    fun `should reject messages that violate domain rules`() {
        listOf(
            message(type = "TRANSFER"),
            message(status = "PENDING"),
            message(amount = "0"),
            message(timestamp = 0),
            message(accountStatus = " "),
        ).forEach { message ->
            assertFailsWith<DomainValidationException> { TransactionProcessedMessageMapper.toDomain(message) }
        }
    }
}