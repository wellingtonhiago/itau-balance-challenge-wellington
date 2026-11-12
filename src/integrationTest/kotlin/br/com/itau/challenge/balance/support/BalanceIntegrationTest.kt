package br.com.itau.challenge.balance.support

import br.com.itau.challenge.balance.infra.DynamoDbInitializer
import br.com.itau.challenge.balance.infra.RedpandaInitializer
import org.junit.jupiter.api.Tag
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

/**
 * Boots the whole application on a random port against DynamoDB Local and Redpanda started by Testcontainers.
 * Every class sharing it reuses the same cached Spring context and containers.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(initializers = [DynamoDbInitializer::class, RedpandaInitializer::class])
annotation class BalanceIntegrationTest