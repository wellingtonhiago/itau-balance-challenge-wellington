package br.com.itau.challenge.balance.adapter.input.kafka.config

import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.util.backoff.BackOffExecution
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TransactionRetryPolicyTest {

    private val policy = TransactionRetryPolicy(
        TransactionsKafkaProperties.Retry(
            initialInterval = Duration.ofMillis(100),
            multiplier = 2.0,
            maxInterval = Duration.ofMillis(400),
            maxAttemptsOnUnexpectedError = 3,
        ),
    )

    @Test
    fun `should retry storage outages indefinitely with capped exponential backoff`() {
        val outage = ListenerExecutionFailedException(
            "listener failed",
            BalanceStorageUnavailableException("DynamoDB is down", RuntimeException()),
        )

        val execution = policy.backOffFor(outage).start()
        val intervals = List(1_000) { execution.nextBackOff() }

        assertEquals(listOf(100L, 200L, 400L, 400L), intervals.take(4))
        assertNotEquals(BackOffExecution.STOP, intervals.last())
    }

    @Test
    fun `should stop retrying unexpected errors after the configured attempts`() {
        val execution = policy.backOffFor(IllegalStateException("bug")).start()

        assertEquals(listOf(100L, 200L, 400L, BackOffExecution.STOP), List(4) { execution.nextBackOff() })
    }
}