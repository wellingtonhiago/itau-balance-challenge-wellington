package br.com.itau.challenge.balance.adapter.output.dynamodb.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.time.Duration

object BalanceStoragePropertiesFixtures {
    fun properties(
        apiCallTimeout: Duration = Duration.ofSeconds(2),
        apiCallAttemptTimeout: Duration = Duration.ofMillis(500),
        maxAttempts: Int = 3,
        minimumNumberOfCalls: Int = 4,
    ) = BalanceStorageProperties(
        apiCallTimeout = apiCallTimeout,
        apiCallAttemptTimeout = apiCallAttemptTimeout,
        maxAttempts = maxAttempts,
        circuitBreaker = BalanceStorageProperties.CircuitBreaker(
            failureRateThreshold = 50f,
            slidingWindowSize = 4,
            minimumNumberOfCalls = minimumNumberOfCalls,
            waitDurationInOpenState = Duration.ofMinutes(1),
            permittedCallsInHalfOpenState = 1,
        ),
    )

    fun circuitBreaker(properties: BalanceStorageProperties = properties()): CircuitBreaker =
        BalanceDynamoDbConfig().run {
            accountBalanceStorageCircuitBreaker(accountBalanceStorageCircuitBreakerRegistry(properties))
        }
}