package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.domain.exception.AccountBalanceNotFoundException
import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import br.com.itau.challenge.balance.domain.model.BalanceFixtures
import br.com.itau.challenge.balance.port.input.GetAccountBalanceUseCase
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.ZoneId
import kotlin.test.Test

class AccountBalanceControllerTest {

    private val accountId = BalanceFixtures.ACCOUNT_ID

    private fun mockMvc(
        zoneId: ZoneId = ZoneId.of("America/Sao_Paulo"),
        useCase: GetAccountBalanceUseCase = GetAccountBalanceUseCase { BalanceFixtures.accountBalance() },
    ): MockMvc =
        MockMvcBuilders
            .standaloneSetup(AccountBalanceController(useCase, zoneId))
            .setControllerAdvice(AccountBalanceExceptionHandler())
            .build()

    @Test
    fun `should return the balance following the response contract`() {
        mockMvc().get("/balances/$accountId").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            content {
                json(
                    """
                    {
                      "id": "$accountId",
                      "owner": "${BalanceFixtures.OWNER_ID}",
                      "balance": {"amount": 183.12, "currency": "BRL"},
                      "updated_at": "2025-07-04T12:02:44.589998-03:00"
                    }
                    """,
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `should format updated_at in the configured time zone`() {
        mockMvc(zoneId = ZoneId.of("UTC")).get("/balances/$accountId").andExpect {
            jsonPath("\$.updated_at") { value("2025-07-04T15:02:44.589998Z") }
        }
    }

    @Test
    fun `should answer 400 with a problem detail for an invalid account id`() {
        listOf("not-a-uuid", "1-1-1-1-1").forEach { invalidId ->
            mockMvc().get("/balances/$invalidId").andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("\$.title") { value("Invalid account id") }
                jsonPath("\$.detail") { value("Account id '$invalidId' is not a valid UUID") }
            }
        }
    }

    @Test
    fun `should answer 404 with a problem detail when the account has no balance`() {
        val useCase = GetAccountBalanceUseCase { throw AccountBalanceNotFoundException(it) }

        mockMvc(useCase = useCase).get("/balances/$accountId").andExpect {
            status { isNotFound() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("\$.status") { value(404) }
            jsonPath("\$.title") { value("Account balance not found") }
            jsonPath("\$.detail") { value("No balance found for account $accountId") }
        }
    }

    @Test
    fun `should answer 503 with retry after when the storage is unavailable`() {
        val useCase = GetAccountBalanceUseCase {
            throw BalanceStorageUnavailableException("Failed to read balance", RuntimeException("timeout"))
        }

        mockMvc(useCase = useCase).get("/balances/$accountId").andExpect {
            status { isServiceUnavailable() }
            header { string("Retry-After", "5") }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("\$.title") { value("Balance service unavailable") }
            jsonPath("\$.detail") { value("The balance storage is temporarily unavailable, please retry later") }
        }
    }

    @Test
    fun `should reject unsupported HTTP methods`() {
        mockMvc().post("/balances/$accountId").andExpect {
            status { isMethodNotAllowed() }
        }
    }
}