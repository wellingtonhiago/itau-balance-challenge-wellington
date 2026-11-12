package br.com.itau.challenge.balance.adapter.output.dynamodb.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("balances.storage")
data class BalanceStorageProperties(
    val apiCallTimeout: Duration,
    val apiCallAttemptTimeout: Duration,
    val maxAttempts: Int,
    val circuitBreaker: CircuitBreaker,
) {
    data class CircuitBreaker(
        val failureRateThreshold: Float,
        val slidingWindowSize: Int,
        val minimumNumberOfCalls: Int,
        val waitDurationInOpenState: Duration,
        val permittedCallsInHalfOpenState: Int,
    )
}