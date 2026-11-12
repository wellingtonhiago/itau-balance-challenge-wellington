package br.com.itau.challenge.balance.adapter.input.kafka

import org.slf4j.MDC

internal inline fun <T> withLoggingContext(vararg entries: Pair<String, Any?>, block: () -> T): T {
    entries.forEach { (key, value) -> MDC.put(key, value.toString()) }
    try {
        return block()
    } finally {
        entries.forEach { (key, _) -> MDC.remove(key) }
    }
}