package br.com.itau.challenge.balance.application

import br.com.itau.challenge.balance.domain.exception.AccountBalanceNotFoundException
import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.port.input.GetAccountBalanceUseCase
import br.com.itau.challenge.balance.port.output.FindAccountBalanceDataProvider
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetAccountBalanceService(
    private val findAccountBalanceDataProvider: FindAccountBalanceDataProvider,
) : GetAccountBalanceUseCase {

    override fun getBalance(accountId: UUID): AccountBalance =
        findAccountBalanceDataProvider.findByAccountId(accountId)
            ?: throw AccountBalanceNotFoundException(accountId)
}