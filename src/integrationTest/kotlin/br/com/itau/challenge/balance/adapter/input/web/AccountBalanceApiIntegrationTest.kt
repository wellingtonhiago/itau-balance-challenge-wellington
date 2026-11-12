package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.domain.model.AccountStatus
import br.com.itau.challenge.balance.domain.model.EventTimestamp
import br.com.itau.challenge.balance.domain.model.Money
import br.com.itau.challenge.balance.port.output.SaveAccountBalanceDataProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.math.BigDecimal
import java.util.UUID

/**
 * Serves `GET /balances/{accountId}` from the real application context backed by DynamoDB Local
 * (see `make db-up`). Run explicitly with `./gradlew integrationTest`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountBalanceApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var saveAccountBalanceDataProvider: SaveAccountBalanceDataProvider

    @Autowired
    private lateinit var dynamoDbClient: DynamoDbClient

    @Value("\${dynamodb.account-balance-table-name}")
    private lateinit var tableName: String

    private val accountId: UUID = UUID.randomUUID()

    @AfterEach
    fun tearDown() {
        dynamoDbClient.deleteItem { request ->
            request.tableName(tableName).key(mapOf("account_id" to AttributeValue.fromS(accountId.toString())))
        }
    }

    @Test
    fun `should return the balance stored in DynamoDB`() {
        val ownerId = UUID.randomUUID()
        saveAccountBalanceDataProvider.saveIfNewer(
            AccountBalance(
                accountId = accountId,
                ownerId = ownerId,
                balance = Money.of(BigDecimal("-10.50"), "BRL"),
                status = AccountStatus("ENABLED"),
                updatedAt = EventTimestamp(1_751_641_364_589_998L),
                lastTransactionId = UUID.randomUUID(),
            ),
        )

        mockMvc.get("/balances/$accountId").andExpect {
            status { isOk() }
            content {
                json(
                    """
                    {
                      "id": "$accountId",
                      "owner": "$ownerId",
                      "balance": {"amount": -10.50, "currency": "BRL"},
                      "updated_at": "2025-07-04T12:02:44.589998-03:00"
                    }
                    """,
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `should answer 404 for an account without balance`() {
        mockMvc.get("/balances/$accountId").andExpect {
            status { isNotFound() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }
}