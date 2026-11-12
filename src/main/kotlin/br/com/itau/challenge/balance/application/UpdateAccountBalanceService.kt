package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.domain.model.TransactionEvent
import br.com.itau.challenge.balance.port.input.UpdateAccountBalanceUseCase
import br.com.itau.challenge.balance.port.output.SaveAccountBalanceDataProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdateAccountBalanceService(
    private val saveAccountBalanceDataProvider: SaveAccountBalanceDataProvider,
) : UpdateAccountBalanceUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun updateBalance(event: TransactionEvent): BalanceUpdateOutcome =
        saveAccountBalanceDataProvider.saveIfNewer(event.toAccountBalance()).also { outcome ->
            val log: (String, Array<Any>) -> Unit = when (outcome) {
                BalanceUpdateOutcome.UPDATED -> logger::debug
                BalanceUpdateOutcome.DUPLICATE, BalanceUpdateOutcome.STALE -> logger::info
            }
            log(
                "Balance update {}: transactionId={} accountId={} timestamp={}",
                arrayOf(outcome, event.transaction.id, event.account.id, event.transaction.timestamp.epochMicros),
            )
        }
}