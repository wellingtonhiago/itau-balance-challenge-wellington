package br.com.itau.challenge.balance.adapter.output.dynamodb

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse

class FakeDynamoDbClient : DynamoDbClient {
    val getItemRequests = mutableListOf<GetItemRequest>()
    val putItemRequests = mutableListOf<PutItemRequest>()

    var onGetItem: () -> GetItemResponse = { GetItemResponse.builder().build() }
    var onPutItem: () -> PutItemResponse = { PutItemResponse.builder().build() }
    var onDescribeTable: (DescribeTableRequest) -> DescribeTableResponse = { DescribeTableResponse.builder().build() }

    override fun getItem(getItemRequest: GetItemRequest): GetItemResponse {
        getItemRequests += getItemRequest
        return onGetItem()
    }

    override fun putItem(putItemRequest: PutItemRequest): PutItemResponse {
        putItemRequests += putItemRequest
        return onPutItem()
    }

    override fun describeTable(describeTableRequest: DescribeTableRequest): DescribeTableResponse =
        onDescribeTable(describeTableRequest)

    override fun serviceName(): String = DynamoDbClient.SERVICE_NAME

    override fun close() = Unit
}