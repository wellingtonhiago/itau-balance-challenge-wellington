package br.com.itau.challenge.balance.support

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.UUID

fun DynamoDbClient.deleteAccountBalance(tableName: String, accountId: UUID) {
    deleteItem { request -> request.tableName(tableName).key(mapOf("account_id" to AttributeValue.fromS(accountId.toString()))) }
}