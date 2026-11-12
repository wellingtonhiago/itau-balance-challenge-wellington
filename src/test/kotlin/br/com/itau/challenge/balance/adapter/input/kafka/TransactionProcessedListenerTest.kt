package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.domain.model.TransactionEvent
import br.com.itau.challenge.balance.port.input.UpdateAccountBalanceUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.support.Acknowledgment
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionProcessedListenerTest {

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val receivedEvents = mutableListOf<TransactionEvent>()
    private val acknowledgment = FakeAcknowledgment()
    private val meterRegistry = SimpleMeterRegistry()

    private fun listener(
        useCase: UpdateAccountBalanceUseCase = UpdateAccountBalanceUseCase {
            receivedEvents += it
            BalanceUpdateOutcome.UPDATED
        },
    ) = TransactionProcessedListener(useCase, objectMapper, TransactionMetrics(meterRegistry))

    private fun record(payload: String?) =
        ConsumerRecord<String?, String?>("transacoes-financeiras-processadas", 0, 0L, null, payload)

    @Test
    fun `should convert the payload into a domain event and acknowledge it`() {
        listener().onMessage(record(TransactionMessageFixtures.json()), acknowledgment)

        val expected = TransactionEvent(BalanceFixtures.transaction(), BalanceFixtures.account())
        assertEquals(listOf(expected), receivedEvents)
        assertTrue(acknowledgment.acknowledged)
    }

    @Test
    fun `should acknowledge duplicate and stale events`() {
        listOf(BalanceUpdateOutcome.DUPLICATE, BalanceUpdateOutcome.STALE).forEach { outcome ->
            val acknowledgment = FakeAcknowledgment()

            listener { outcome }.onMessage(record(TransactionMessageFixtures.json()), acknowledgment)

            assertTrue(acknowledgment.acknowledged, "expected $outcome to be acknowledged")
        }
        assertEquals(1.0, processedCount("duplicate"))
        assertEquals(1.0, processedCount("stale"))
    }

    @Test
    fun `should expose the transaction in the logging context only while processing`() {
        val mdcDuringProcessing = mutableMapOf<String, String>()
        listener {
            mdcDuringProcessing.putAll(MDC.getCopyOfContextMap())
            BalanceUpdateOutcome.UPDATED
        }.onMessage(record(TransactionMessageFixtures.json()), acknowledgment)

        assertEquals(BalanceFixtures.TRANSACTION_ID.toString(), mdcDuringProcessing["transaction.id"])
        assertEquals(BalanceFixtures.ACCOUNT_ID.toString(), mdcDuringProcessing["account.id"])
        assertEquals("transacoes-financeiras-processadas", mdcDuringProcessing["kafka.topic"])
        assertEquals(1.0, processedCount("updated"))
        assertTrue(MDC.getCopyOfContextMap().isNullOrEmpty())
    }

    private fun processedCount(outcome: String): Double? =
        meterRegistry.find(TransactionMetrics.PROCESSED).tag(TransactionMetrics.OUTCOME_TAG, outcome).counter()?.count()

    @Test
    fun `should reject malformed JSON without acknowledging`() {
        assertFailsWith<InvalidTransactionMessageException> {
            listener().onMessage(record("{not-json"), acknowledgment)
        }

        assertTrue(receivedEvents.isEmpty())
        assertFalse(acknowledgment.acknowledged)
        assertTrue(meterRegistry.find(TransactionMetrics.PROCESSED).counters().isEmpty())
        assertTrue(MDC.getCopyOfContextMap().isNullOrEmpty())
    }

    @Test
    fun `should reject a payload with missing fields`() {
        assertFailsWith<InvalidTransactionMessageException> {
            listener().onMessage(record("""{"transaction": {"id": "x"}}"""), acknowledgment)
        }
    }

    @Test
    fun `should reject an empty payload`() {
        listOf(null, "", "   ").forEach { payload ->
            assertFailsWith<InvalidTransactionMessageException> {
                listener().onMessage(record(payload), acknowledgment)
            }
        }
        assertFalse(acknowledgment.acknowledged)
    }

    @Test
    fun `should propagate domain validation errors`() {
        assertFailsWith<DomainValidationException> {
            listener().onMessage(record(TransactionMessageFixtures.json(currency = "XYZ")), acknowledgment)
        }
        assertFalse(acknowledgment.acknowledged)
    }

    @Test
    fun `should not acknowledge when storage is unavailable`() {
        val failingUseCase = UpdateAccountBalanceUseCase {
            throw BalanceStorageUnavailableException("DynamoDB is down", RuntimeException())
        }

        assertFailsWith<BalanceStorageUnavailableException> {
            listener(failingUseCase).onMessage(record(TransactionMessageFixtures.json()), acknowledgment)
        }
        assertFalse(acknowledgment.acknowledged)
    }

    private class FakeAcknowledgment : Acknowledgment {
        var acknowledged = false
            private set

        override fun acknowledge() {
            acknowledged = true
        }
    }
}