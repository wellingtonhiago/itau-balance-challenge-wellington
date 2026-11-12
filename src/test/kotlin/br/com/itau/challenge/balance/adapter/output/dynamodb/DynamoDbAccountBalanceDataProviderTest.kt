package br.com.itau.challenge.balance.adapter.output.dynamodb

import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.ACCOUNT_ID
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.TIMESTAMP_MICROS
import br.com.itau.challenge.balance.domain.model.BalanceFixtures.accountBalance
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamoDbAccountBalanceDataProviderTest {

    private val client = FakeDynamoDbClient()
    private val provider = DynamoDbAccountBalanceDataProvider(client, "AccountBalances")

    @Test
    fun `should find the balance with a strongly consistent read by account id`() {
        client.onGetItem = { GetItemResponse.builder().item(AccountBalanceItemMapper.toItem(accountBalance())).build() }

        val balance = provider.findByAccountId(ACCOUNT_ID)

        assertEquals(accountBalance(), balance)
        val request = client.getItemRequests.single()
        assertEquals("AccountBalances", request.tableName())
        assertEquals(ACCOUNT_ID.toString(), request.key().getValue("account_id").s())
        assertTrue(request.consistentRead())
    }

    @Test
    fun `should return null when the account has no balance`() {
        assertNull(provider.findByAccountId(ACCOUNT_ID))
    }

    @Test
    fun `should translate read failures into storage unavailable`() {
        client.onGetItem = { throw SdkClientException.create("connection refused") }

        val exception = assertFailsWith<BalanceStorageUnavailableException> { provider.findByAccountId(ACCOUNT_ID) }

        assertEquals("Failed to read balance of account $ACCOUNT_ID in table 'AccountBalances'", exception.message)
    }

    @Test
    fun `should put the balance only if it is newer than the stored one`() {
        val outcome = provider.saveIfNewer(accountBalance())

        assertEquals(BalanceUpdateOutcome.UPDATED, outcome)
        val request = client.putItemRequests.single()
        assertEquals("AccountBalances", request.tableName())
        assertEquals(AccountBalanceItemMapper.toItem(accountBalance()), request.item())
        assertEquals(
            "attribute_not_exists(account_id) OR updated_at < :updatedAt " +
                "OR (updated_at = :updatedAt AND last_transaction_id < :transactionId)",
            request.conditionExpression(),
        )
        assertEquals(TIMESTAMP_MICROS.toString(), request.expressionAttributeValues().getValue(":updatedAt").n())
        assertEquals(
            accountBalance().lastTransactionId.toString(),
            request.expressionAttributeValues().getValue(":transactionId").s(),
        )
        assertEquals(ReturnValuesOnConditionCheckFailure.ALL_OLD, request.returnValuesOnConditionCheckFailure())
    }

    @Test
    fun `should report duplicate when the stored balance came from the same transaction`() {
        client.onPutItem = { throw conditionFailedWithStored(accountBalance().let(AccountBalanceItemMapper::toItem)) }

        assertEquals(BalanceUpdateOutcome.DUPLICATE, provider.saveIfNewer(accountBalance()))
    }

    @Test
    fun `should report stale when the stored balance came from a newer transaction`() {
        val newer = accountBalance(timestampMicros = TIMESTAMP_MICROS + 1, transactionId = UUID.randomUUID()) // TODO: confirmar argumento cortado na foto
        client.onPutItem = { throw conditionFailedWithStored(AccountBalanceItemMapper.toItem(newer)) }

        assertEquals(BalanceUpdateOutcome.STALE, provider.saveIfNewer(accountBalance()))
    }

    @Test
    fun `should report stale when the stored item is not returned`() {
        client.onPutItem = { throw ConditionalCheckFailedException.builder().message("condition failed").build() }

        assertEquals(BalanceUpdateOutcome.STALE, provider.saveIfNewer(accountBalance()))
    }

    @Test
    fun `should translate write failures into storage unavailable`() {
        client.onPutItem = { throw ProvisionedThroughputExceededException.builder().message("th...").build() } // TODO: mensagem cortada na foto

        val exception = assertFailsWith<BalanceStorageUnavailableException> { provider.saveIfNewer(accountBalance()) }

        assertEquals("Failed to save balance of account $ACCOUNT_ID in table 'AccountBalances'", exception.message)
    }

    private fun conditionFailedWithStored(item: Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>) =
        ConditionalCheckFailedException.builder().message("condition failed").item(item).build()
}