package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.adapter.input.web.dto.AccountBalanceResponse
import br.com.itau.challenge.balance.domain.model.CanonicalUuid
import br.com.itau.challenge.balance.port.input.GetAccountBalanceUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId

@RestController
class AccountBalanceController(
    private val getAccountBalanceUseCase: GetAccountBalanceUseCase,
    @Value("\${balances.api.time-zone}") private val zoneId: ZoneId,
) {

    @GetMapping("/balances/{accountId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBalance(@PathVariable accountId: String): AccountBalanceResponse {
        val id = CanonicalUuid.parseOrNull(accountId) ?: throw InvalidAccountIdException(accountId)
        return AccountBalanceResponse.from(getAccountBalanceUseCase.getBalance(id), zoneId)
    }
}