package br.com.itau.challenge.balance

import br.com.itau.challenge.balance.adapter.input.kafka.config.TransactionsKafkaProperties
import br.com.itau.challenge.balance.adapter.output.dynamodb.config.ACCOUNT_BALANCE_DYNAMODB_CLIENT
import br.com.itau.challenge.balance.builder.INVALID_JSON
import br.com.itau.challenge.balance.builder.PayloadObjectsBuilder
import br.com.itau.challenge.balance.builder.TRANSACTION_PROCESSED_EVENT
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.junit.StringResource
import br.com.itau.challenge.balance.support.BalanceIntegrationTest
import br.com.itau.challenge.balance.support.deleteAccountBalance
import br.com.itau.challenge.balance.support.eventually
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ONE_SECOND_MICROS = 1_000_000L
private val TIMEOUT: Duration = Duration.ofSeconds(30)

/** Publishes events to Redpanda and reads the resulting balance through the HTTP API backed by DynamoDB Local. */
@BalanceIntegrationTest
class BalanceEndToEndIntegrationTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val consumerFactory: ConsumerFactory<String, String>,
    private val properties: TransactionsKafkaProperties,
    @Qualifier(ACCOUNT_BALANCE_DYNAMODB_CLIENT) private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.account-balance-table-name}") private val tableName: String,
) {

    private val accountId: UUID = BalanceFixtures.ACCOUNT_ID

    @AfterEach
    fun tearDown() = dynamoDbClient.deleteAccountBalance(tableName, accountId)

    @Test
    fun `should serve the newest balance even after stale, duplicated and invalid events`(
        @StringResource(TRANSACTION_PROCESSED_EVENT) event: String,
        @StringResource(INVALID_JSON) invalidJson: String,
        @StringResource("http/response/get-balance-200.json") expectedResponse: String,
    ) {
        // Given
        publish(event)
        val firstResponse = eventually(TIMEOUT) { getBalance(accountId).takeIf { it.statusCode == HttpStatus.OK } }
        JSONAssert.assertEquals(expectedResponse, firstResponse.body, JSONCompareMode.STRICT)

        // When
        publish(
            PayloadObjectsBuilder.transactionProcessedEvent(
                transactionId = UUID.randomUUID(),
                timestampMicros = BalanceFixtures.TIMESTAMP_MICROS - ONE_SECOND_MICROS,
                balance = "50.00",
            ),
        )
        publish(event)
        publish(invalidJson)
        // Records sharing the account key land on the same partition in order: once the invalid
        // record reaches the DLT, the stale and duplicated events were already handled.
        awaitDeadLetter(invalidJson)

        // Then
        val finalResponse = getBalance(accountId)
        assertEquals(HttpStatus.OK, finalResponse.statusCode)
        JSONAssert.assertEquals(expectedResponse, finalResponse.body, JSONCompareMode.STRICT)
    }

    @Test
    fun `should answer 404 with a problem detail for an account without events`(
        @StringResource("http/response/get-balance-404.json") expectedResponse: String,
    ) {
        // Given
        val accountWithoutEvents = UUID.randomUUID()

        // When
        val response = getBalance(accountWithoutEvents)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.headers.contentType))
        JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.LENIENT)
    }

    private fun publish(payload: String) {
        kafkaTemplate.send(properties.topicName, accountId.toString(), payload).get()
    }

    private fun getBalance(id: UUID): ResponseEntity<String> = testRestTemplate.getForEntity("/balances/$id", String::class.java)

    private fun awaitDeadLetter(payload: String) {
        val overrides = Properties().apply { put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest") }
        consumerFactory.createConsumer("e2e-dlt-verifier-${UUID.randomUUID()}", null, null, overrides).use { consumer ->
            consumer.subscribe(listOf(properties.dltTopicName))
            eventually(TIMEOUT) {
                consumer.poll(Duration.ofMillis(500)).firstOrNull { it.key() == accountId.toString() && it.value() == payload }
            }
        }
    }
}