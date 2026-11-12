package br.com.itau.challenge.balance.adapter.output.dynamodb.config

import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

const val ACCOUNT_BALANCE_DYNAMODB_CLIENT = "accountBalanceDynamoDbClient"
const val ACCOUNT_BALANCE_STORAGE_CIRCUIT_BREAKER = "account-balance-storage"

@Configuration
@EnableConfigurationProperties(BalanceStorageProperties::class)
class BalanceDynamoDbConfig {

    private val logger = LoggerFactory.getLogger(BalanceDynamoDbConfig::class.java)

    /** Not a default candidate, so the hello example keeps injecting its own client by type. */
    @Bean(ACCOUNT_BALANCE_DYNAMODB_CLIENT, defaultCandidate = false)
    fun accountBalanceDynamoDbClient(
        @Value("\${dynamodb.endpoint}") endpoint: String,
        @Value("\${dynamodb.region}") region: String,
        properties: BalanceStorageProperties,
    ): DynamoDbClient =
        DynamoDbClient
            .builder()
            .region(Region.of(region))
            .apply {
                // Without an endpoint override (real AWS), the SDK default credentials provider chain is used.
                if (endpoint.isNotBlank()) {
                    endpointOverride(URI.create(endpoint))
                    credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                }
            }
            .overrideConfiguration { override ->
                override
                    .apiCallTimeout(properties.apiCallTimeout)
                    .apiCallAttemptTimeout(properties.apiCallAttemptTimeout)
                    .retryStrategy(
                        AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(properties.maxAttempts).build(),
                    )
            }.build()

    @Bean
    fun accountBalanceStorageCircuitBreakerRegistry(properties: BalanceStorageProperties): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(circuitBreakerConfig(properties.circuitBreaker))

    @Bean
    fun accountBalanceStorageCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker =
        registry.circuitBreaker(ACCOUNT_BALANCE_STORAGE_CIRCUIT_BREAKER).apply {
            eventPublisher.onStateTransition { event ->
                logger.warn("Circuit breaker '{}' changed state: {}", event.circuitBreakerName, event.stateTransition)
            }
        }

    @Bean
    fun accountBalanceStorageCircuitBreakerMetrics(registry: CircuitBreakerRegistry): MeterBinder =
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)

    private fun circuitBreakerConfig(settings: BalanceStorageProperties.CircuitBreaker): CircuitBreakerConfig =
        CircuitBreakerConfig
            .custom()
            .failureRateThreshold(settings.failureRateThreshold)
            .slidingWindowSize(settings.slidingWindowSize)
            .minimumNumberOfCalls(settings.minimumNumberOfCalls)
            .waitDurationInOpenState(settings.waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState)
            .recordExceptions(BalanceStorageUnavailableException::class.java)
            .build()
}