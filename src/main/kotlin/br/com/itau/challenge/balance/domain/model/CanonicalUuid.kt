package br.com.itau.challenge.balance.domain.model

import java.util.UUID

/** Strict UUID parsing: [UUID.fromString] also accepts non canonical forms such as `1-1-1-1-1`. */
object CanonicalUuid {
    fun parseOrNull(value: String): UUID? =
        runCatching { UUID.fromString(value) }
            .getOrNull()
            ?.takeIf { it.toString() == value.lowercase() }
}