package br.com.itau.challenge.balance.adapter.output.dynamodb.health

import br.com.itau.challenge.balance.adapter.output.dynamodb.config.ACCOUNT_BALANCE_DYNAMODB_CLIENT
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.TableStatus

/**
 * Probes the balance table directly (bypassing the circuit breaker) so the reported status reflects
 * DynamoDB itself, while the circuit breaker state is exposed as a detail.
 */
@Component("accountBalanceStorage")
class AccountBalanceStorageHealthIndicator(
    @Qualifier(ACCOUNT_BALANCE_DYNAMODB_CLIENT)
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.account-balance-table-name}")
    private val tableName: String,
    private val circuitBreaker: CircuitBreaker,
) : HealthIndicator {

    override fun health(): Health {
        val builder = try {
            val table = dynamoDbClient.describeTable { it.tableName(tableName) }.table()
            val status = if (table.tableStatus() == TableStatus.ACTIVE) Health.up() else Health.outOfService()
            status.withDetail("tableStatus", table.tableStatusAsString())
        } catch (exception: SdkException) {
            Health.down().withDetail("error", exception.javaClass.simpleName)
        }

        return builder
            .withDetail("table", tableName)
            .withDetail("circuitBreaker", circuitBreaker.state.name)
            .build()
    }
}