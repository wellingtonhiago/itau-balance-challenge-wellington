package br.com.itau.challenge.balance.port.output

import br.com.itau.challenge.balance.domain.model.AccountBalance
import br.com.itau.challenge.balance.domain.model.BalanceUpdateOutcome

fun interface SaveAccountBalanceDataProvider {
    /**
     * Persists [balance] only when it is newer than the stored one (see [AccountBalance.isNewerThan]),
     * atomically, so concurrent and out-of-order deliveries never overwrite a more recent balance.
     */
    fun saveIfNewer(balance: AccountBalance): BalanceUpdateOutcome
}