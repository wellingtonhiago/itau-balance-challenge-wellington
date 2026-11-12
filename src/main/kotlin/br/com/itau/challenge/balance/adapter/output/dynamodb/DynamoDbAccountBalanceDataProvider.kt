package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.adapter.output.dynamodb.AccountBalanceItemMapper.LAST_TRANSACTION_ID
import br.com.itau.challenge.balance.adapter.output.dynamodb.AccountBalanceItemMapper.UPDATED_AT
import br.com.itau.challenge.balance.adapter.output.dynamodb.AccountBalanceItemMapper.key
import br.com.itau.challenge.balance.adapter.output.dynamodb.AccountBalanceItemMapper.number
import br.com.itau.challenge.balance.adapter.output.dynamodb.AccountBalanceItemMapper.string
import br.com.itau.challenge.balance.adapter.output.dynamodb.AccountBalanceItemMapper.toDomain
import br.com.itau.challenge.balance.adapter.output.dynamodb.AccountBalanceItemMapper.toItem
import br.com.itau.challenge.balance.adapter.output.dynamodb.config.ACCOUNT_BALANCE_DYNAMODB_CLIENT
import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.port.output.FindAccountBalanceDataProvider
import br.com.itau.challenge.balance.port.output.SaveAccountBalanceDataProvider
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure
import java.util.UUID

private const val ONLY_IF_NEWER_CONDITION =
    "attribute_not_exists(account_id) OR updated_at < :updatedAt " +
        "OR (updated_at = :updatedAt AND last_transaction_id < :transactionId)"

@Component
class DynamoDbAccountBalanceDataProvider(
    @Qualifier(ACCOUNT_BALANCE_DYNAMODB_CLIENT) private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.account-balance-table-name}") private val tableName: String,
    private val circuitBreaker: CircuitBreaker,
) : FindAccountBalanceDataProvider,
    SaveAccountBalanceDataProvider {

    override fun findByAccountId(accountId: UUID): AccountBalance? {
        val request = GetItemRequest
            .builder()
            .tableName(tableName)
            .key(key(accountId))
            .consistentRead(true)
            .build()

        val response = translatingFailures("read balance of account $accountId") { dynamoDbClient.getItem(request) }

        return response.item().takeIf { response.hasItem() }?.let(::toDomain)
    }

    override fun saveIfNewer(balance: AccountBalance): BalanceUpdateOutcome {
        val request = PutItemRequest
            .builder()
            .tableName(tableName)
            .item(toItem(balance))
            .conditionExpression(ONLY_IF_NEWER_CONDITION)
            .expressionAttributeValues(
                mapOf(
                    ":updatedAt" to number(balance.updatedAt.epochMicros.toString()),
                    ":transactionId" to string(balance.lastTransactionId.toString()),
                ),
            ).returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
            .build()

        return try {
            translatingFailures("save balance of account ${balance.accountId}") { dynamoDbClient.putItem(request) }
            BalanceUpdateOutcome.UPDATED
        } catch (exception: ConditionalCheckFailedException) {
            outcomeOfRejected(balance, exception)
        }
    }

    private fun outcomeOfRejected(
        balance: AccountBalance,
        exception: ConditionalCheckFailedException,
    ): BalanceUpdateOutcome {
        val stored = exception.item().orEmpty()
        val isSameTransaction =
            stored[LAST_TRANSACTION_ID]?.s() == balance.lastTransactionId.toString() &&
                stored[UPDATED_AT]?.n() == balance.updatedAt.epochMicros.toString()
        return if (isSameTransaction) BalanceUpdateOutcome.DUPLICATE else BalanceUpdateOutcome.STALE
    }

    private fun <T> translatingFailures(operation: String, block: () -> T): T = try {
        circuitBreaker.executeSupplier {
            try {
                block()
            } catch (exception: ConditionalCheckFailedException) {
                throw exception
            } catch (exception: SdkException) {
                throw BalanceStorageUnavailableException("Failed to $operation in table '$tableName'", exception)
            }
        }
    } catch (exception: CallNotPermittedException) {
        throw BalanceStorageUnavailableException("Circuit breaker is open, skipped $operation in table '$tableName'", exception)
    }
}