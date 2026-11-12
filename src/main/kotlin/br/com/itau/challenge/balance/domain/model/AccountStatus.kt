package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.ensure

@JvmInline
value class AccountStatus(val value: String) {

    init {
        ensure(value.isNotBlank()) { "Account status must not be blank" }
    }
}