package br.com.itau.challenge.balance.domain.model

import br.com.itau.challenge.balance.domain.exception.DomainValidationException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `should normalize the amount to the currency minor unit`() {
        val money = Money.of(BigDecimal("183.1"), "BRL")

        assertEquals(BigDecimal("183.10"), money.amount)
        assertEquals("BRL", money.currency.currencyCode)
    }

    @Test
    fun `should be equal regardless of the amount scale received`() {
        assertEquals(Money.of(BigDecimal("10"), "BRL"), Money.of(BigDecimal("10.00"), "BRL"))
    }

    @Test
    fun `should accept negative amounts for overdrawn balances`() {
        assertEquals(BigDecimal("-5.00"), Money.of(BigDecimal("-5"), "BRL").amount)
    }

    @Test
    fun `should keep the amount untouched when the currency has no minor unit`() {
        assertEquals(BigDecimal("1.23456"), Money.of(BigDecimal("1.23456"), "XAU").amount)
    }

    @Test
    fun `should reject a currency that is not ISO 4217`() {
        val exception = assertFailsWith<DomainValidationException> { Money.of(BigDecimal.ONE, "XYZ") }

        assertEquals("Currency 'XYZ' is not a valid ISO 4217 code", exception.message)
    }

    @Test
    fun `should reject a lowercase currency code`() {
        assertFailsWith<DomainValidationException> { Money.of(BigDecimal.ONE, "brl") }
    }

    @Test
    fun `should reject an amount with more decimal places than the currency allows`() {
        val exception = assertFailsWith<DomainValidationException> { Money.of(BigDecimal("1.001"), "BRL") }

        assertEquals("Amount 1.001 exceeds 2 decimal places allowed for BRL", exception.message)
    }

    @Test
    fun `should tell whether the amount is positive`() {
        assertTrue(Money.of(BigDecimal("0.01"), "BRL").isPositive)
        assertFalse(Money.of(BigDecimal.ZERO, "BRL").isPositive)
        assertFalse(Money.of(BigDecimal("-0.01"), "BRL").isPositive)
    }
}