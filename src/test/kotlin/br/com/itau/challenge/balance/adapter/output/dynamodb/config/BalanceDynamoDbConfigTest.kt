package br.com.itau.challenge.balance.adapter.output.dynamodb.config

import br.com.itau.challenge.balance.adapter.output.dynamodb.DynamoDbAccountBalanceDataProvider
import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.ACCOUNT_ID
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException
import software.amazon.awssdk.core.exception.ApiCallTimeoutException
import software.amazon.awssdk.regions.Region
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.measureTime

class BalanceDynamoDbConfigTest {

    private val config = BalanceDynamoDbConfig()

    @Test
    fun `should configure the DynamoDB client with the storage timeouts and retry attempts`() {
        config.accountBalanceDynamoDbClient("http://localhost:8000", "us-east-1", BalanceStoragePropertiesFixtures.properties()).use { client ->
            val override = client.serviceClientConfiguration().overrideConfiguration()

            assertEquals(Duration.ofSeconds(2), override.apiCallTimeout().get())
            assertEquals(Duration.ofMillis(500), override.apiCallAttemptTimeout().get())
            assertEquals(3, override.retryStrategy().get().maxAttempts())
        }
    }

    @Test
    fun `should fail fast when DynamoDB accepts the connection but never answers`() {
        val properties = BalanceStoragePropertiesFixtures.properties(
            apiCallTimeout = Duration.ofMillis(800),
            apiCallAttemptTimeout = Duration.ofMillis(200),
        )

        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { silentServer ->
            config.accountBalanceDynamoDbClient("http://localhost:${silentServer.localPort}", "us-east-1", properties).use { client ->
                val provider = DynamoDbAccountBalanceDataProvider(client, "AccountBalances", CircuitBreaker.ofDefaults("test"))

                lateinit var exception: BalanceStorageUnavailableException
                val elapsed = measureTime {
                    exception = assertFailsWith { provider.findByAccountId(ACCOUNT_ID) }
                }

                assertTrue(
                    exception.cause is ApiCallTimeoutException || exception.cause is ApiCallAttemptTimeoutException,
                    "expected an SDK timeout, got \${exception.cause}",
                )
                assertTrue(elapsed.inWholeMilliseconds < 3_000, "expected to give up quickly, took $elapsed")
            }
        }
    }

    @Test
    fun `should target the regional AWS endpoint when no endpoint override is configured`() {
        config.accountBalanceDynamoDbClient("", "sa-east-1", BalanceStoragePropertiesFixtures.properties()).use { client ->
            val clientConfiguration = client.serviceClientConfiguration()

            assertEquals(Region.SA_EAST_1, clientConfiguration.region())
            assertTrue(clientConfiguration.endpointOverride().isEmpty)
        }
    }

    @Test
    fun `should configure the circuit breaker to record only storage failures`() {
        val circuitBreaker = BalanceStoragePropertiesFixtures.circuitBreaker()
        val circuitBreakerConfig = circuitBreaker.circuitBreakerConfig

        assertEquals(ACCOUNT_BALANCE_STORAGE_CIRCUIT_BREAKER, circuitBreaker.name)
        assertEquals(50f, circuitBreakerConfig.failureRateThreshold)
        assertEquals(4, circuitBreakerConfig.slidingWindowSize)
        assertEquals(4, circuitBreakerConfig.minimumNumberOfCalls)
        assertEquals(1, circuitBreakerConfig.permittedNumberOfCallsInHalfOpenState)
        assertTrue(circuitBreakerConfig.recordExceptionPredicate.test(BalanceStorageUnavailableException("down", RuntimeException())))
        assertTrue(!circuitBreakerConfig.recordExceptionPredicate.test(IllegalStateException("bug")))
    }
}