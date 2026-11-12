package br.com.itau.challenge.balance.adapter.input.web

import br.com.itau.challenge.balance.domain.exception.AccountBalanceNotFoundException
import br.com.itau.challenge.balance.domain.exception.BalanceStorageUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private const val RETRY_AFTER_SECONDS = "5"

@RestControllerAdvice(assignableTypes = [AccountBalanceController::class])
class AccountBalanceExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidAccountIdException::class)
    fun handleInvalidAccountId(exception: InvalidAccountIdException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Invalid account id", exception.message)

    @ExceptionHandler(AccountBalanceNotFoundException::class)
    fun handleNotFound(exception: AccountBalanceNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Account balance not found", exception.message)

    @ExceptionHandler(BalanceStorageUnavailableException::class)
    fun handleStorageUnavailable(exception: BalanceStorageUnavailableException): ResponseEntity<ProblemDetail> {
        logger.warn("Balance storage unavailable: {} (cause: {})", exception.message, exception.cause.toString())
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
            .body(
                problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Balance service unavailable",
                    "The balance storage is temporarily unavailable, please retry later",
                ),
            )
    }

    private fun problem(status: HttpStatus, title: String, detail: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply { this.title = title }
}