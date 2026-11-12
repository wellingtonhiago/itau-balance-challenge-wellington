package br.com.itau.challenge.balance

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Exposes health probes and metrics through Actuator against the local infrastructure
 * (see `make db-up`). Run explicitly with `./gradlew integrationTest`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should report the balance storage in the health endpoint`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
            jsonPath("$.components.accountBalanceStorage.status") { value("UP") }
            jsonPath("$.components.accountBalanceStorage.details.table") { value("AccountBalances") }
            jsonPath("$.components.accountBalanceStorage.details.tableStatus") { value("ACTIVE") }
            jsonPath("$.components.accountBalanceStorage.details.circuitBreaker") { value("CLOSED") }
        }
    }

    @Test
    fun `should expose liveness and readiness probes`() {
        listOf("/actuator/health/liveness", "/actuator/health/readiness").forEach { probe ->
            mockMvc.get(probe).andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
        }
    }

    @Test
    fun `should expose the circuit breaker state as a metric`() {
        mockMvc.get("/actuator/metrics/resilience4j.circuitbreaker.state") {
            param("tag", "name:account-balance-storage", "state:closed")
        }.andExpect {
            status { isOk() }
            jsonPath("$.measurements[0].value") { value(1.0) }
        }
    }
}