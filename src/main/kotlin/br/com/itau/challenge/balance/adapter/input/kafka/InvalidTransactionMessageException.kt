package br.com.itau.challenge.balance.adapter.input.kafka

class InvalidTransactionMessageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)