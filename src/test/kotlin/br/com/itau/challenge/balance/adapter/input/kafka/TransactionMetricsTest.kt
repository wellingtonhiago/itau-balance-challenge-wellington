package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.domain.model.TransactionEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.kafka.listener.ListenerExecutionFailedException
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionMetricsTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val event = TransactionEvent(BalanceFixtures.transaction(), BalanceFixtures.account())
    private val processedAt = event.transaction.timestamp.toInstant().plus(Duration.ofSeconds(3))
    private val metrics = TransactionMetrics(meterRegistry, Clock.fixed(processedAt, ZoneOffset.UTC))

    @Test
    fun `should count processed events by outcome and record their age`() {
        metrics.recordProcessed(event, BalanceUpdateOutcome.UPDATED)
        metrics.recordProcessed(event, BalanceUpdateOutcome.UPDATED)
        metrics.recordProcessed(event, BalanceUpdateOutcome.STALE)

        assertEquals(2.0, counter(TransactionMetrics.PROCESSED, TransactionMetrics.OUTCOME_TAG, "updated"))
        assertEquals(1.0, counter(TransactionMetrics.PROCESSED, TransactionMetrics.OUTCOME_TAG, "stale"))

        val eventAge = meterRegistry.get(TransactionMetrics.EVENT_AGE).timer()
        assertEquals(3, eventAge.count())
        assertEquals(3.0, eventAge.max(TimeUnit.SECONDS))
    }

    @Test
    fun `should tag dead-lettered events with the listener failure cause`() {
        metrics.recordDeadLettered(
            ListenerExecutionFailedException("listener failed", InvalidTransactionMessageException("bad payload")),
        )
        metrics.recordDeadLettered(DomainValidationException("invalid currency"))

        assertEquals(
            1.0,
            counter(TransactionMetrics.DEAD_LETTERED, TransactionMetrics.EXCEPTION_TAG, "InvalidTransactionMessageException"),
        )
        assertEquals(
            1.0,
            counter(TransactionMetrics.DEAD_LETTERED, TransactionMetrics.EXCEPTION_TAG, "DomainValidationException"),
        )
    }

    private fun counter(name: String, tag: String, value: String): Double =
        meterRegistry.get(name).tag(tag, value).counter().count()
}