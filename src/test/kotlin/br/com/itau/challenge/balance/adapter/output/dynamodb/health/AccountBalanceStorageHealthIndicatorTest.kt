package br.com.itau.challenge.balance.adapter.output.dynamodb.health

import br.com.itau.challenge.balance.adapter.output.dynamodb.FakeDynamoDbClient
import br.com.itau.challenge.balance.adapter.output.dynamodb.config.BalanceStoragePropertiesFixtures
import org.springframework.boot.health.contributor.Status
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse
import software.amazon.awssdk.services.dynamodb.model.TableDescription
import software.amazon.awssdk.services.dynamodb.model.TableStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBalanceStorageHealthIndicatorTest {

    private val dynamoDbClient = FakeDynamoDbClient()
    private val circuitBreaker = BalanceStoragePropertiesFixtures.circuitBreaker()
    private val healthIndicator = AccountBalanceStorageHealthIndicator(dynamoDbClient, TABLE_NAME, circuitBreaker)

    @Test
    fun `should be up when the balance table is active`() {
        var describedTable: String? = null
        dynamoDbClient.onDescribeTable = { request ->
            describedTable = request.tableName()
            tableWithStatus(TableStatus.ACTIVE)
        }

        val health = healthIndicator.health()

        assertEquals(Status.UP, health.status)
        assertEquals(TABLE_NAME, describedTable)
        assertEquals(
            mapOf("tableStatus" to "ACTIVE", "table" to TABLE_NAME, "circuitBreaker" to "CLOSED"),
            health.details,
        )
    }

    @Test
    fun `should be out of service while the balance table is not active`() {
        dynamoDbClient.onDescribeTable = { tableWithStatus(TableStatus.UPDATING) }

        val health = healthIndicator.health()

        assertEquals(Status.OUT_OF_SERVICE, health.status)
        assertEquals("UPDATING", health.details["tableStatus"])
    }

    @Test
    fun `should be down without leaking error details when DynamoDB is unreachable`() {
        dynamoDbClient.onDescribeTable = { throw SdkClientException.create("Unable to connect to http://internal-host:8000") }
        circuitBreaker.transitionToOpenState()

        val health = healthIndicator.health()

        assertEquals(Status.DOWN, health.status)
        assertEquals(
            mapOf("error" to "SdkClientException", "table" to TABLE_NAME, "circuitBreaker" to "OPEN"),
            health.details,
        )
    }

    private fun tableWithStatus(status: TableStatus) =
        DescribeTableResponse.builder()
            .table(TableDescription.builder().tableName(TABLE_NAME).tableStatus(status).build())
            .build()

    private companion object {
        const val TABLE_NAME = "AccountBalances"
    }
}