package br.com.itau.challenge.balance.domain.exception

class DomainValidationException(message: String) : RuntimeException(message)

internal inline fun ensure(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw DomainValidationException(lazyMessage())
}

internal inline fun <reified E : Enum<E>> parseEnum(value: String, field: String): E =
    enumValues<E>().firstOrNull { it.name == value }
        ?: throw DomainValidationException("Unknown $field '$value'. Expected one of ${enumValues<E>().joinToString()}")