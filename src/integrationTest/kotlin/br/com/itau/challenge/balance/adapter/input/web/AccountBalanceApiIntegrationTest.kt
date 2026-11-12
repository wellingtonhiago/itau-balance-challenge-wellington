package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.output.dynamodb.config.ACCOUNT_BALANCE_DYNAMODB_CLIENT
import br.com.itau.challenge.balance.builder.DomainObjectsBuilder.accountBalance
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.junit.StringResource
import br.com.itau.challenge.balance.port.output.SaveAccountBalanceDataProvider
import br.com.itau.challenge.balance.support.BalanceIntegrationTest
import br.com.itau.challenge.balance.support.deleteAccountBalance
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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Calls `GET /balances/{accountId}` over HTTP against the application backed by DynamoDB Local. */
@BalanceIntegrationTest
class AccountBalanceApiIntegrationTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val saveAccountBalanceDataProvider: SaveAccountBalanceDataProvider,
    @Qualifier(ACCOUNT_BALANCE_DYNAMODB_CLIENT) private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.account-balance-table-name}") private val tableName: String,
) {

    @AfterEach
    fun tearDown() = dynamoDbClient.deleteAccountBalance(tableName, BalanceFixtures.ACCOUNT_ID)

    @Test
    fun `should return the balance stored in DynamoDB`(
        @StringResource("http/response/get-balance-200.json") expectedResponse: String,
    ) {
        // Given
        saveAccountBalanceDataProvider.saveIfNewer(accountBalance())

        // When
        val response = testRestTemplate.getForEntity("/balances/${BalanceFixtures.ACCOUNT_ID}", String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(response.headers.contentType))
        JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.STRICT)
    }

    @Test
    fun `should return a negative balance stored in DynamoDB`(
        @StringResource("http/response/get-balance-negative-200.json") expectedResponse: String,
    ) {
        // Given
        saveAccountBalanceDataProvider.saveIfNewer(accountBalance(amount = "-10.50"))

        // When
        val response = testRestTemplate.getForEntity("/balances/${BalanceFixtures.ACCOUNT_ID}", String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(response.headers.contentType))
        JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.STRICT)
    }

    @Test
    fun `should answer 404 for an account without balance`(
        @StringResource("http/response/get-balance-404.json") expectedResponse: String,
    ) {
        // Given
        val accountWithoutBalance = UUID.randomUUID()

        // When
        val response = testRestTemplate.getForEntity("/balances/$accountWithoutBalance", String::class.java)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.headers.contentType))
        JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.LENIENT)
    }

    @Test
    fun `should answer 400 when the account id is not a UUID`(
        @StringResource("http/response/get-balance-400.json") expectedResponse: String,
    ) {
        // Given
        val invalidAccountId = "abc"

        // When
        val response = testRestTemplate.getForEntity("/balances/$invalidAccountId", String::class.java)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.headers.contentType))
        JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.LENIENT)
    }
}