package br.com.itau.challenge.balance.adapter.input.kafka

import br.com.itau.challenge.balance.adapter.input.kafka.config.TRANSACTIONS_CONTAINER_FACTORY
import br.com.itau.challenge.balance.adapter.input.kafka.dto.TransactionProcessedMessage
import br.com.itau.challenge.balance.port.input.UpdateAccountBalanceUseCase
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@Component
class TransactionProcessedListener(
    private val updateAccountBalanceUseCase: UpdateAccountBalanceUseCase,
    private val objectMapper: ObjectMapper,
    private val metrics: TransactionMetrics,
) {

    @KafkaListener(
        id = "transaction-processed-listener",
        topics = ["\${transactions.topic-name}"],
        groupId = "\${transactions.consumer.group-id}",
        containerFactory = TRANSACTIONS_CONTAINER_FACTORY,
    )
    fun onMessage(record: ConsumerRecord<String?, String?>, acknowledgment: Acknowledgment) = withLoggingContext(
        "kafka.topic" to record.topic(),
        "kafka.partition" to record.partition(),
        "kafka.offset" to record.offset(),
    ) {
        val event = TransactionProcessedMessageMapper.toDomain(parse(record.value()))
        withLoggingContext("transaction.id" to event.transaction.id, "account.id" to event.account.id) {
            val outcome = updateAccountBalanceUseCase.updateBalance(event)
            metrics.recordProcessed(event, outcome)
            acknowledgment.acknowledge()
        }
    }

    private fun parse(payload: String?): TransactionProcessedMessage {
        if (payload.isNullOrBlank()) throw InvalidTransactionMessageException("Message payload is empty")
        return try {
            objectMapper.readValue(payload, TransactionProcessedMessage::class.java)
        } catch (exception: JacksonException) {
            throw InvalidTransactionMessageException(
                "Message payload is not a valid transaction event: ${exception.originalMessage}",
                exception,
            )
        }
    }
}