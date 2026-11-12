package br.com.itau.challenge.balance.domain.model

enum class BalanceUpdateOutcome {
    UPDATED,
    DUPLICATE,
    STALE,
}