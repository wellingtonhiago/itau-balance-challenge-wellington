package br.com.itau.challenge.balance.adapter.output.dynamodb

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.domain.model.AccountStatus
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import java.math.BigDecimal
import java.net.URI
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the balance adapter against a running DynamoDB Local (see `make db-up`).
 * Run explicitly with `./gradlew integrationTest`.
 */
class DynamoDbAccountBalanceIntegrationTest {

    private val tableName = System.getenv("ACCOUNT_BALANCE_TABLE_NAME") ?: "AccountBalances"

    private val dynamoDbClient: DynamoDbClient =
        DynamoDbClient
            .builder()
            .endpointOverride(URI.create(System.getenv("DYNAMODB_ENDPOINT") ?: "http://localhost:8000")) // TODO: default cortado na foto
            .region(Region.of(System.getenv("DYNAMODB_REGION") ?: "us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))) // TODO: credenciais cortadas na foto
            .build()

    private val provider = DynamoDbAccountBalanceDataProvider(dynamoDbClient, tableName, CircuitBreaker.ofDefaults("account-balance-storage"))

    private val accountId: UUID = UUID.randomUUID()

    @AfterEach
    fun tearDown() {
        dynamoDbClient.deleteItem(
            DeleteItemRequest
                .builder()
                .tableName(tableName)
                .key(mapOf("account_id" to AttributeValue.builder().s(accountId.toString()).build()))
                .build(),
        )
    }

    @Test
    fun `should return null for an account without balance`() {
        assertNull(provider.findByAccountId(accountId))
    }

    @Test
    fun `should keep the most recent balance regardless of arrival order`() {
        val first = balance(timestampMicros = 1_000, amount = "10.00")
        val second = balance(timestampMicros = 2_000, amount = "20.00")

        assertEquals(BalanceUpdateOutcome.UPDATED, provider.saveIfNewer(second))
        assertEquals(BalanceUpdateOutcome.STALE, provider.saveIfNewer(first))
        assertEquals(second, provider.findByAccountId(accountId))
    }

    @Test
    fun `should ignore a redelivered transaction`() {
        val balance = balance(timestampMicros = 1_000, amount = "10.00")

        assertEquals(BalanceUpdateOutcome.UPDATED, provider.saveIfNewer(balance))
        assertEquals(BalanceUpdateOutcome.DUPLICATE, provider.saveIfNewer(balance))
        assertEquals(balance, provider.findByAccountId(accountId))
    }

    @Test
    fun `should replace the balance with a newer transaction`() {
        provider.saveIfNewer(balance(timestampMicros = 1_000, amount = "10.00"))
        val newer = balance(timestampMicros = 1_001, amount = "-5.50", status = "BLOCKED")

        assertEquals(BalanceUpdateOutcome.UPDATED, provider.saveIfNewer(newer))
        assertEquals(newer, provider.findByAccountId(accountId))
    }

    @Test
    fun `should converge to the most recent balance under concurrent writes`() {
        val balances = (1..50).map { balance(timestampMicros = it * 1_000L, amount = "$it.00") }
        val executor = Executors.newFixedThreadPool(16)

        try {
            executor.invokeAll(balances.map { Callable { provider.saveIfNewer(it) } }).forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        assertEquals(balances.maxBy { it.updatedAt }, provider.findByAccountId(accountId))
    }

    private fun balance(timestampMicros: Long, amount: String, status: String = "ENABLED") = AccountBalance(
        accountId = accountId,
        ownerId = UUID.fromString("315e3cfe-f4af-4cd2-b298-a449e614349a"),
        balance = Money.of(BigDecimal(amount), "BRL"),
        status = AccountStatus(status),
        updatedAt = EventTimestamp(timestampMicros),
        lastTransactionId = UUID.randomUUID(),
    )
}