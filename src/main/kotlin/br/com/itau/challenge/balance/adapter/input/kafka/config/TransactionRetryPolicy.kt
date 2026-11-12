package br.com.itau.challenge.balance.adapter.input.kafka.config

import br.com.itau.challenge.balance.adapter.input.kafka.InvalidTransactionMessageException
import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.ExponentialBackOff

/**
 * - Invalid messages are never retried: they go straight to the DLT.
 * - Storage outages are retried indefinitely, so no message is lost or skipped while DynamoDB is down.
 * - Any other failure is retried a few times before going to the DLT.
 */
internal class TransactionRetryPolicy(private val retry: TransactionsKafkaProperties.Retry) {

    fun backOffFor(exception: Exception): BackOff =
        if (exception.hasCause<BalanceStorageUnavailableException>()) {
            exponentialBackOff()
        } else {
            exponentialBackOff().apply { maxAttempts = retry.maxAttemptsOnUnexpectedError }
        }

    private fun exponentialBackOff() = ExponentialBackOff(retry.initialInterval.toMillis(), retry.multiplier).apply {
        maxInterval = retry.maxInterval.toMillis()
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is T }

    companion object {
        val NON_RETRYABLE_EXCEPTIONS = arrayOf(
            InvalidTransactionMessageException::class.java,
            DomainValidationException::class.java,
        )
    }
}