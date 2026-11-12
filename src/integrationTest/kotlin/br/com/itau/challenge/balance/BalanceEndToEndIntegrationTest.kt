package br.com.itau.challenge.balance

import br.com.itau.challenge.balance.adapter.input.kafka.config.TransactionsKafkaProperties
import br.com.itau.challenge.balance.adapter.output.dynamodb.config.ACCOUNT_BALANCE_DYNAMODB_CLIENT
import br.com.itau.challenge.balance.support.eventually
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.annotation.DirtiesContext
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NEWEST_TIMESTAMP = 1_751_641_364_589_998L
private const val ONE_SECOND_MICROS = 1_000_000L

/**
 * Drives the whole flow through the real infrastructure (see `make db-up kafka-up`): events are published
 * to the transactions topic, consumed into DynamoDB Local and read back over HTTP from `GET /balances/{accountId}`.
 * Run explicitly with `./gradlew integrationTest`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BalanceEndToEndIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var consumerFactory: ConsumerFactory<String, String>

    @Autowired
    private lateinit var properties: TransactionsKafkaProperties

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    @Qualifier(ACCOUNT_BALANCE_DYNAMODB_CLIENT)
    private lateinit var dynamoDbClient: DynamoDbClient

    @Value("\${dynamodb.account-balance-table-name}")
    private lateinit var tableName: String

    private val httpClient = HttpClient.newHttpClient()
    private val accountId: UUID = UUID.randomUUID()
    private val ownerId: UUID = UUID.randomUUID()

    @AfterEach
    fun cleanUp() {
        dynamoDbClient.deleteItem { request ->
            request.tableName(tableName).key(mapOf("account_id" to AttributeValue.fromS(accountId.toString())))
        }
    }

    @Test
    fun `should serve the newest balance even after stale, duplicated and invalid events`() {
        val newestTransactionId = UUID.randomUUID()
        val expected = """
            {"id": "$accountId", "owner": "$ownerId", "balance": {"amount": 183.12, "currency": "BRL"},
             "updated_at": "2025-07-04T12:02:44.589998-03:00"}
        """

        publish(event(newestTransactionId, NEWEST_TIMESTAMP, balance = "183.12"))
        val firstResponse = eventually(Duration.ofSeconds(30)) {
            getBalance(accountId).takeIf { it.statusCode() == 200 }
        }
        assertJsonEquals(expected, firstResponse.body())

        publish(event(UUID.randomUUID(), NEWEST_TIMESTAMP - ONE_SECOND_MICROS, balance = "50.00"))
        publish(event(newestTransactionId, NEWEST_TIMESTAMP, balance = "183.12"))
        val invalid = "{not-json $accountId"
        publish(invalid)
        // Records sharing the account key land on the same partition in order: once the invalid
        // record reaches the DLT, the stale and duplicated events were already handled.
        awaitDeadLetter(invalid)

        val finalResponse = getBalance(accountId)
        assertEquals(200, finalResponse.statusCode())
        assertJsonEquals(expected, finalResponse.body())
    }

    @Test
    fun `should answer 404 with a problem detail for an account without events`() {
        val response = getBalance(UUID.randomUUID())

        assertEquals(404, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"))
    }

    private fun publish(payload: String) {
        kafkaTemplate.send(properties.topicName, accountId.toString(), payload).get()
    }

    private fun getBalance(id: UUID): HttpResponse<String> =
        httpClient.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/balances/$id")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun awaitDeadLetter(payload: String) {
        val overrides = Properties().apply { put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest") }
        consumerFactory.createConsumer("e2e-dlt-verifier-${UUID.randomUUID()}", null, null, overrides).use { consumer ->
            consumer.subscribe(listOf(properties.dltTopicName))
            eventually(Duration.ofSeconds(30)) {
                consumer.poll(Duration.ofMillis(500)).firstOrNull { it.key() == accountId.toString() && it.value() == payload }
            }
        }
    }

    private fun assertJsonEquals(expected: String, actual: String) =
        assertEquals(objectMapper.readTree(expected), objectMapper.readTree(actual))

    private fun event(transactionId: UUID, timestamp: Long, balance: String) =
        """
        {
          "transaction": {"id": "$transactionId", "type": "CREDIT", "amount": 1.00, "currency": "BRL",
                          "status": "APPROVED", "timestamp": $timestamp},
          "account": {"id": "$accountId", "owner": "$ownerId", "created_at": "1634874339",
                      "status": "ENABLED", "balance": {"amount": $balance, "currency": "BRL"}}
        }
        """.trimIndent()
}