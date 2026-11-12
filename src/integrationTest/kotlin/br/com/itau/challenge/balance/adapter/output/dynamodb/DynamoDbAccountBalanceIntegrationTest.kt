package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.adapter.output.dynamodb.config.ACCOUNT_BALANCE_DYNAMODB_CLIENT
import br.com.itau.challenge.balance.builder.DomainObjectsBuilder
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.support.BalanceIntegrationTest
import br.com.itau.challenge.balance.support.deleteAccountBalance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Exercises the balance adapter bean with conditional writes against DynamoDB Local. */
@BalanceIntegrationTest
class DynamoDbAccountBalanceIntegrationTest @Autowired constructor(
    private val provider: DynamoDbAccountBalanceDataProvider,
    @Qualifier(ACCOUNT_BALANCE_DYNAMODB_CLIENT) private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.account-balance-table-name}") private val tableName: String,
) {

    private val accountId: UUID = UUID.randomUUID()

    @AfterEach
    fun tearDown() = dynamoDbClient.deleteAccountBalance(tableName, accountId)

    @Test
    fun `should return null for an account without balance`() {
        // When
        val stored = provider.findByAccountId(accountId)

        // Then
        assertNull(stored)
    }

    @Test
    fun `should keep the most recent balance regardless of arrival order`() {
        // Given
        val first = balance(timestampMicros = 1_000, amount = "10.00")
        val second = balance(timestampMicros = 2_000, amount = "20.00")

        // When
        val secondOutcome = provider.saveIfNewer(second)
        val firstOutcome = provider.saveIfNewer(first)

        // Then
        assertEquals(BalanceUpdateOutcome.UPDATED, secondOutcome)
        assertEquals(BalanceUpdateOutcome.STALE, firstOutcome)
        assertEquals(second, provider.findByAccountId(accountId))
    }

    @Test
    fun `should ignore a redelivered transaction`() {
        // Given
        val balance = balance(timestampMicros = 1_000, amount = "10.00")
        val initialOutcome = provider.saveIfNewer(balance)

        // When
        val outcome = provider.saveIfNewer(balance)

        // Then
        assertEquals(BalanceUpdateOutcome.UPDATED, initialOutcome)
        assertEquals(BalanceUpdateOutcome.DUPLICATE, outcome)
        assertEquals(balance, provider.findByAccountId(accountId))
    }

    @Test
    fun `should replace the balance with a newer transaction`() {
        // Given
        provider.saveIfNewer(balance(timestampMicros = 1_000, amount = "10.00"))
        val newer = balance(timestampMicros = 1_001, amount = "-5.50", status = "BLOCKED")

        // When
        val outcome = provider.saveIfNewer(newer)

        // Then
        assertEquals(BalanceUpdateOutcome.UPDATED, outcome)
        assertEquals(newer, provider.findByAccountId(accountId))
    }

    @Test
    fun `should converge to the most recent balance under concurrent writes`() {
        // Given
        val balances = (1..50).map { balance(timestampMicros = it * 1_000L, amount = "$it.00") }.shuffled(Random(9))
        val executor = Executors.newFixedThreadPool(16)

        // When
        try {
            executor.invokeAll(balances.map { Callable { provider.saveIfNewer(it) } }).forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        // Then
        assertEquals(balances.maxBy { it.updatedAt }, provider.findByAccountId(accountId))
    }

    private fun balance(timestampMicros: Long, amount: String, status: String = "ENABLED") =
        DomainObjectsBuilder.accountBalance(accountId = accountId, amount = amount, status = status, timestampMicros = timestampMicros)
}