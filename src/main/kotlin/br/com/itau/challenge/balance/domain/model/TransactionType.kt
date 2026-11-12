package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.parseEnum

enum class TransactionType {
    CREDIT,
    DEBIT,
    ;

    companion object {
        fun from(value: String): TransactionType = parseEnum(value, "transaction type")
    }
}