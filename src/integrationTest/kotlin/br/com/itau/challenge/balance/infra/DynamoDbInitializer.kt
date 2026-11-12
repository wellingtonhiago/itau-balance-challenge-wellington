package br.com.itau.challenge.balance.infra

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.net.URI

private const val DYNAMODB_IMAGE = "amazon/dynamodb-local:3.3.0"
private const val DYNAMODB_PORT = 8000

/** Starts a DynamoDB Local container once per JVM and creates the `AccountBalances` table. */
class DynamoDbInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val environment = applicationContext.environment
        TestPropertyValues.of("dynamodb.endpoint=$endpoint").applyTo(environment)
        createTable(environment.getRequiredProperty("dynamodb.account-balance-table-name"), environment.getRequiredProperty("dynamodb.region"))
    }

    private fun createTable(tableName: String, region: String) {
        DynamoDbClient
            .builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
            .build()
            .use { client ->
                if (tableName in client.listTables().tableNames()) return
                client.createTable { request ->
                    request
                        .tableName(tableName)
                        .attributeDefinitions(AttributeDefinition.builder().attributeName("account_id").attributeType(ScalarAttributeType.S).build())
                        .keySchema(KeySchemaElement.builder().attributeName("account_id").keyType(KeyType.HASH).build())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                }
                client.waiter().waitUntilTableExists { it.tableName(tableName) }
            }
    }

    private companion object {
        val endpoint: String by lazy {
            val container = GenericContainer<Nothing>(DockerImageName.parse(DYNAMODB_IMAGE)).apply {
                setCommand("-jar", "DynamoDBLocal.jar", "-inMemory")
                addExposedPort(DYNAMODB_PORT)
                start()
            }
            "http://${container.host}:${container.getMappedPort(DYNAMODB_PORT)}"
        }
    }
}