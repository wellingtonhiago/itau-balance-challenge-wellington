package br.com.itau.challenge.balance.adapter.input.kafka.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("transactions")
data class TransactionsKafkaProperties(
    val topicName: String,
    val dltTopicName: String,
    val consumer: Consumer,
    val retry: Retry,
) {
    data class Consumer(val groupId: String, val concurrency: Int)

    data class Retry(
        val initialInterval: Duration,
        val multiplier: Double,
        val maxInterval: Duration,
        val maxAttemptsOnUnexpectedError: Long,
    )
}