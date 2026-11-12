package br.com.itau.challenge.balance.port.input

import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome
import br.com.itau.challenge.balance.domain.model.TransactionEvent

fun interface UpdateAccountBalanceUseCase {
    fun updateBalance(event: TransactionEvent): BalanceUpdateOutcome
}