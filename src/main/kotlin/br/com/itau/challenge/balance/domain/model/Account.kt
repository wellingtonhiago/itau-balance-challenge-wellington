package br.com.itau.challenge.balance.domain.model

import java.util.UUID

data class Account(
    val id: UUID,
    val ownerId: UUID,
    val status: AccountStatus,
    val balance: Money,
)