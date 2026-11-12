package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.ensure
import java.time.Instant
import java.time.temporal.ChronoUnit

@JvmInline
value class EventTimestamp(val epochMicros: Long) : Comparable<EventTimestamp> {

    init {
        ensure(epochMicros > 0) { "Timestamp must be a positive epoch in microseconds, got $epochMicros" }
    }

    fun toInstant(): Instant = Instant.EPOCH.plus(epochMicros, ChronoUnit.MICROS)

    override fun compareTo(other: EventTimestamp): Int = epochMicros.compareTo(other.epochMicros)
}