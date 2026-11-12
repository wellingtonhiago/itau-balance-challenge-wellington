package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.domain.model.TransactionEvent
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class TransactionMetrics(
    private val meterRegistry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun recordProcessed(event: TransactionEvent, outcome: BalanceUpdateOutcome) {
        Counter.builder(PROCESSED)
            .description("Transaction events applied to the balance store, by outcome")
            .tag(OUTCOME_TAG, outcome.name.lowercase())
            .register(meterRegistry)
            .increment()

        Timer.builder(EVENT_AGE)
            .description("Time between the transaction event and its processing")
            .register(meterRegistry)
            .record(Duration.between(event.transaction.timestamp.toInstant(), clock.instant()))
    }

    fun recordDeadLettered(exception: Throwable) {
        val cause = if (exception is ListenerExecutionFailedException) exception.cause ?: exception else exception
        Counter.builder(DEAD_LETTERED)
            .description("Transaction events sent to the DLT, by failure cause")
            .tag(EXCEPTION_TAG, cause.javaClass.simpleName)
            .register(meterRegistry)
            .increment()
    }

    companion object {
        const val PROCESSED = "balance.transactions.processed"
        const val EVENT_AGE = "balance.transactions.event.age"
        const val DEAD_LETTERED = "balance.transactions.dead.lettered"
        const val OUTCOME_TAG = "outcome"
        const val EXCEPTION_TAG = "exception"
    }
}