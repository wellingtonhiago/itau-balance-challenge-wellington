package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.adapter.input.kafka.config.TransactionsKafkaProperties
import br.com.itau.challenge.balance.adapter.output.dynamodb.config.ACCOUNT_BALANCE_DYNAMODB_CLIENT
import br.com.itau.challenge.balance.builder.INVALID_JSON
import br.com.itau.challenge.balance.builder.PayloadObjectsBuilder.transactionProcessedEvent
import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.junit.StringResource
import br.com.itau.challenge.balance.port.output.FindAccountBalanceDataProvider
import br.com.itau.challenge.balance.support.BalanceIntegrationTest
import br.com.itau.challenge.balance.support.deleteAccountBalance
import br.com.itau.challenge.balance.support.eventually
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.math.BigDecimal
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val BASE_TIMESTAMP = 1_751_641_364_589_998L
private const val PARTITION = 0

/** Drives the real listener through the transactions topic on Redpanda, persisting into DynamoDB Local. */
@BalanceIntegrationTest
class TransactionProcessedListenerIntegrationTest @Autowired constructor(
    private val meterRegistry: MeterRegistry,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val consumerFactory: ConsumerFactory<String, String>,
    private val findAccountBalanceDataProvider: FindAccountBalanceDataProvider,
    private val properties: TransactionsKafkaProperties,
    @Qualifier(ACCOUNT_BALANCE_DYNAMODB_CLIENT) private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.account-balance-table-name}") private val tableName: String,
) {

    private val accountId: UUID = UUID.randomUUID()
    private val createdAccounts = mutableListOf(accountId)

    @AfterEach
    fun tearDown() = createdAccounts.forEach { dynamoDbClient.deleteAccountBalance(tableName, it) }

    @Test
    fun `should persist the balance carried by a published event`() {
        // Given
        val transactionId = UUID.randomUUID()

        // When
        publish(event(transactionId, BASE_TIMESTAMP, balance = "183.12"))

        // Then
        val stored = awaitBalance { it.lastTransactionId == transactionId }
        assertEquals(BigDecimal("183.12"), stored.balance.amount)
    }

    @Test
    fun `should keep the newest balance when events arrive out of order or duplicated`() {
        // Given
        val newest = UUID.randomUUID()
        val newestEvent = event(newest, BASE_TIMESTAMP + 2, balance = "300.00")

        // When
        publish(newestEvent)
        publish(event(UUID.randomUUID(), BASE_TIMESTAMP + 1, balance = "200.00"))
        publish(newestEvent)
        publish(event(UUID.randomUUID(), BASE_TIMESTAMP, balance = "100.00"))
        awaitPartitionDrained()

        // Then
        val stored = assertNotNull(findAccountBalanceDataProvider.findByAccountId(accountId))
        assertEquals(newest, stored.lastTransactionId)
        assertEquals(BigDecimal("300.00"), stored.balance.amount)
    }

    @Test
    fun `should route malformed and invalid messages to the DLT`(@StringResource(INVALID_JSON) malformed: String) {
        // Given
        val invalid = event(UUID.randomUUID(), BASE_TIMESTAMP, balance = "1.00", currency = "XYZ")

        // When
        publish(malformed)
        publish(invalid)

        // Then
        val deadLetters = awaitDeadLetters(count = 2)
        assertEquals(setOf(malformed, invalid), deadLetters.map { it.value() }.toSet())
        assertEquals(
            setOf(
                InvalidTransactionMessageException::class.qualifiedName,
                "br.com.itau.challenge.balance.domain.exception.DomainValidationException",
            ),
            deadLetters.map { it.header("kafka_dlt-exception-cause-fqcn") }.toSet(),
        )
        listOf("InvalidTransactionMessageException", "DomainValidationException").forEach { cause ->
            eventually<Any> {
                meterRegistry.find(TransactionMetrics.DEAD_LETTERED)
                    .tag(TransactionMetrics.EXCEPTION_TAG, cause)
                    .counter()
                    ?.takeIf { it.count() >= 1.0 }
            }
        }
    }

    private fun publish(payload: String, key: UUID = accountId) {
        kafkaTemplate.send(properties.topicName, PARTITION, key.toString(), payload).get()
    }

    private fun event(transactionId: UUID, timestamp: Long, balance: String, currency: String = "BRL") =
        transactionProcessedEvent(accountId, transactionId, timestamp, balance, currency)

    /** Records of a partition are consumed in order: once a sentinel published last is stored, all previous were processed. */
    private fun awaitPartitionDrained() {
        val sentinelAccountId = UUID.randomUUID().also(createdAccounts::add)
        publish(transactionProcessedEvent(sentinelAccountId, UUID.randomUUID(), BASE_TIMESTAMP, "0.00"), key = sentinelAccountId)
        eventually { findAccountBalanceDataProvider.findByAccountId(sentinelAccountId) }
    }

    private fun awaitBalance(condition: (AccountBalance) -> Boolean): AccountBalance =
        eventually { findAccountBalanceDataProvider.findByAccountId(accountId)?.takeIf(condition) }

    private fun awaitDeadLetters(count: Int): List<ConsumerRecord<String, String>> {
        val overrides = Properties().apply { put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest") }
        consumerFactory.createConsumer("dlt-verifier-${UUID.randomUUID()}", null, null, overrides).use { consumer ->
            consumer.subscribe(listOf(properties.dltTopicName))
            val received = mutableListOf<ConsumerRecord<String, String>>()
            return eventually {
                received += consumer.poll(Duration.ofMillis(500)).filter { it.key() == accountId.toString() }
                received.takeIf { it.size >= count }
            }
        }
    }

    private fun ConsumerRecord<String, String>.header(name: String): String? =
        headers().lastHeader(name)?.value()?.decodeToString()
}