package br.com.itau.challenge.balance.support

import java.time.Duration
import kotlin.test.fail

private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(20)

/** Polls [probe] until it returns a non-null value, failing the test once [timeout] elapses. */
fun <T : Any> eventually(timeout: Duration = DEFAULT_TIMEOUT, probe: () -> T?): T {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (System.nanoTime() < deadline) {
        probe()?.let { return it }
        Thread.sleep(200)
    }
    fail("Condition not met within $timeout")
}