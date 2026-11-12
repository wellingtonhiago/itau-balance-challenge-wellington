package br.com.itau.challenge.balance.domain.exception

import java.util.UUID

class AccountBalanceNotFoundException(accountId: UUID) : RuntimeException("No balance found for account $accountId")