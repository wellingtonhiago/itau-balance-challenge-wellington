package br.com.itau.challenge.balance.adapter.input.kafka.config

import br.com.itau.challenge.balance.adapter.input.kafka.TransactionMetrics
import org.apache.kafka.common.TopicPartition
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler

const val TRANSACTIONS_CONTAINER_FACTORY = "transactionsKafkaListenerContainerFactory"

@Configuration
@EnableConfigurationProperties(TransactionsKafkaProperties::class)
class TransactionsKafkaConfig {

    @Bean(TRANSACTIONS_CONTAINER_FACTORY)
    fun transactionsKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        kafkaOperations: KafkaOperations<String, String>,
        properties: TransactionsKafkaProperties,
        metrics: TransactionMetrics,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setConcurrency(properties.consumer.concurrency)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setCommonErrorHandler(errorHandler(kafkaOperations, properties, metrics))
        }

    private fun errorHandler(
        kafkaOperations: KafkaOperations<String, String>,
        properties: TransactionsKafkaProperties,
        metrics: TransactionMetrics,
    ): DefaultErrorHandler {
        val deadLetterRecoverer = DeadLetterPublishingRecoverer(kafkaOperations) { record, _ ->
            TopicPartition(properties.dltTopicName, record.partition())
        }
        val countingRecoverer = ConsumerRecordRecoverer { record, exception ->
            deadLetterRecoverer.accept(record, exception)
            metrics.recordDeadLettered(exception)
        }
        val retryPolicy = TransactionRetryPolicy(properties.retry)

        return DefaultErrorHandler(countingRecoverer).apply {
            setBackOffFunction { _, exception -> retryPolicy.backOffFor(exception) }
            addNotRetryableExceptions(*TransactionRetryPolicy.NON_RETRYABLE_EXCEPTIONS)
        }
    }
}