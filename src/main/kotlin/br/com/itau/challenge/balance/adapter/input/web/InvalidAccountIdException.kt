package br.com.itau.challenge.balance.adapter.input.web

class InvalidAccountIdException(accountId: String) : RuntimeException("Account id '$accountId' is not a valid UUID")