package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.adapter.input.kafka.config.TransactionsKafkaProperties
import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.port.output.FindAccountBalanceDataProvider
import br.com.itau.challenge.balance.support.eventually
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val BASE_TIMESTAMP = 1_751_641_364_589_998L
private const val PARTITION = 0

/**
 * Boots the real application against the Redpanda and DynamoDB Local containers
 * (see `make db-up kafka-up`) and drives the transactions topic end to end.
 * Run explicitly with `./gradlew integrationTest`.
 */
@SpringBootTest
class TransactionProcessedListenerIntegrationTest {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var consumerFactory: ConsumerFactory<String, String>

    @Autowired
    private lateinit var findAccountBalanceDataProvider: FindAccountBalanceDataProvider

    @Autowired
    private lateinit var properties: TransactionsKafkaProperties

    @Autowired
    private lateinit var dynamoDbClient: DynamoDbClient

    @Value("\${dynamodb.account-balance-table-name}")
    private lateinit var tableName: String

    private val accountId: UUID = UUID.randomUUID()
    private val createdAccounts = mutableListOf(accountId)

    @AfterEach
    fun tearDown() {
        createdAccounts.forEach { id ->
            dynamoDbClient.deleteItem { request ->
                request.tableName(tableName).key(mapOf("account_id" to AttributeValue.fromS(id.toString())))
            }
        }
    }

    @Test
    fun `should persist the balance carried by a published event`() {
        val transactionId = UUID.randomUUID()

        publish(event(transactionId, BASE_TIMESTAMP, balance = "183.12"))

        val stored = awaitBalance { it.lastTransactionId == transactionId }
        assertEquals(BigDecimal("183.12"), stored.balance.amount)
    }

    @Test
    fun `should keep the newest balance when events arrive out of order or duplicated`() {
        val newest = UUID.randomUUID()
        val newestEvent = event(newest, BASE_TIMESTAMP + 2, balance = "300.00")

        publish(newestEvent)
        publish(event(UUID.randomUUID(), BASE_TIMESTAMP + 1, balance = "200.00"))
        publish(newestEvent)
        publish(event(UUID.randomUUID(), BASE_TIMESTAMP, balance = "100.00"))
        awaitPartitionDrained()

        val stored = assertNotNull(findAccountBalanceDataProvider.findByAccountId(accountId))
        assertEquals(newest, stored.lastTransactionId)
        assertEquals(BigDecimal("300.00"), stored.balance.amount)
    }

    @Test
    fun `should route malformed and invalid messages to the DLT`() {
        val malformed = "{not-json $accountId"
        val invalid = event(UUID.randomUUID(), BASE_TIMESTAMP, balance = "1.00", currency = "XYZ")

        publish(malformed)
        publish(invalid)

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

    private fun publish(payload: String) {
        kafkaTemplate.send(properties.topicName, PARTITION, accountId.toString(), payload).get()
    }

    private fun event(transactionId: UUID, timestamp: Long, balance: String, currency: String = "BRL") =
        """
        {
          "transaction": {"id": "$transactionId", "type": "CREDIT", "amount": 1.00, "currency": "$currency",
                          "status": "APPROVED", "timestamp": $timestamp},
          "account": {"id": "$accountId", "owner": "${UUID.randomUUID()}", "created_at": "1634874339",
                      "status": "ENABLED", "balance": {"amount": $balance, "currency": "BRL"}}
        }
        """.trimIndent()

    /** Records of a partition are consumed in order: once a sentinel published last is stored, all previous were processed. */
    private fun awaitPartitionDrained() {
        val sentinelAccountId = UUID.randomUUID().also(createdAccounts::add)
        val sentinel = event(UUID.randomUUID(), BASE_TIMESTAMP, balance = "0.00").replace(
            accountId.toString(),
            sentinelAccountId.toString(),
        )
        kafkaTemplate.send(properties.topicName, PARTITION, sentinelAccountId.toString(), sentinel).get()
        eventually { findAccountBalanceDataProvider.findByAccountId(sentinelAccountId) }
    }

    private fun awaitBalance(condition: (AccountBalance) -> Boolean): AccountBalance =
        eventually { findAccountBalanceDataProvider.findByAccountId(accountId)?.takeIf(condition) }

    private fun awaitDeadLetters(count: Int): List<ConsumerRecord<String, String>> {
        val overrides = java.util.Properties().apply {
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }
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