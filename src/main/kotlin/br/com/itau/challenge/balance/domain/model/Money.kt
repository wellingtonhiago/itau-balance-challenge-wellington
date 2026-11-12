package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import br.com.itau.challenge.balance.domain.exception.ensure
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

data class Money private constructor(val amount: BigDecimal, val currency: Currency) {

    val isPositive: Boolean get() = amount.signum() > 0

    companion object {
        private val ISO_4217_CODES = Currency.getAvailableCurrencies().map(Currency::getCurrencyCode).toSet()

        fun of(amount: BigDecimal, currencyCode: String): Money {
            ensure(currencyCode in ISO_4217_CODES) { "Currency '$currencyCode' is not a valid ISO 4217 code" }
            val currency = Currency.getInstance(currencyCode)
            return Money(amount.toMinorUnitScale(currency), currency)
        }

        private fun BigDecimal.toMinorUnitScale(currency: Currency): BigDecimal {
            val fractionDigits = currency.defaultFractionDigits
            if (fractionDigits < 0) return this
            return try {
                setScale(fractionDigits, RoundingMode.UNNECESSARY)
            } catch (_: ArithmeticException) {
                throw DomainValidationException(
                    "Amount $this exceeds $fractionDigits decimal places allowed for ${currency.currencyCode}"
                )
            }
        }
    }
}