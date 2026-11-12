package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.parseEnum

enum class TransactionStatus {
    APPROVED,
    DECLINED,
    ;

    companion object {
        fun from(value: String): TransactionStatus = parseEnum(value, "transaction status")
    }
}