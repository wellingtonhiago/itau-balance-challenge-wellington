package br.com.itau.challenge.balance

import br.com.itau.challenge.balance.junit.StringResource
import br.com.itau.challenge.balance.support.BalanceIntegrationTest
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

/** Calls the Actuator health probes and metrics over HTTP with DynamoDB Local and Redpanda up. */
@BalanceIntegrationTest
class ObservabilityIntegrationTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
) {

    @Test
    fun `should report the balance storage in the health endpoint`(
        @StringResource("http/response/health-up.json") expectedResponse: String,
    ) {
        // When
        val response = testRestTemplate.getForEntity("/actuator/health", String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.LENIENT)
    }

    @Test
    fun `should expose liveness and readiness probes`(
        @StringResource("http/response/health-probe-up.json") expectedResponse: String,
    ) {
        listOf("/actuator/health/liveness", "/actuator/health/readiness").forEach { probe ->
            // When
            val response = testRestTemplate.getForEntity(probe, String::class.java)

            // Then
            assertEquals(HttpStatus.OK, response.statusCode, probe)
            JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.STRICT)
        }
    }

    @Test
    fun `should expose the circuit breaker state as a metric`(
        @StringResource("http/response/circuit-breaker-closed-metric.json") expectedResponse: String,
    ) {
        // When
        val response = testRestTemplate.getForEntity(
            "/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:account-balance-storage&tag=state:closed",
            String::class.java,
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        JSONAssert.assertEquals(expectedResponse, response.body, JSONCompareMode.LENIENT)
    }
}