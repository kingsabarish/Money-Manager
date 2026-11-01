package com.moneymanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    @Test
    fun evaluateSimpleAddition() {
        val result = CalculatorEngine.evaluate("100 + 250")
        assertNotNull(result)
        assertEquals("350", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun evaluateSimpleSubtraction() {
        val result = CalculatorEngine.evaluate("1200 - 350")
        assertNotNull(result)
        assertEquals("850", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun evaluateDivisionSplit() {
        val result = CalculatorEngine.evaluate("1200 / 3")
        assertNotNull(result)
        assertEquals("400", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun evaluateDivisionWithDecimals() {
        val result = CalculatorEngine.evaluate("100 / 3")
        assertNotNull(result)
        assertEquals("33.33", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun evaluateWithUnicodeOperators() {
        val result = CalculatorEngine.evaluate("1500 − 300 × 2 ÷ 4")
        assertNotNull(result)
        // 1500 - (600 / 4) = 1500 - 150 = 1350
        assertEquals("1350", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun evaluateWithParentheses() {
        val result = CalculatorEngine.evaluate("(1500 - 300) / 3")
        assertNotNull(result)
        assertEquals("400", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun evaluateDivisionByZeroReturnsNull() {
        val result = CalculatorEngine.evaluate("100 / 0")
        assertNull(result)
    }

    @Test
    fun evaluatePartialWithTrailingOperator() {
        val result = CalculatorEngine.evaluatePartial("1500 - ")
        assertNotNull(result)
        assertEquals("1500", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun evaluatePartialWithUnclosedParenthesis() {
        val result = CalculatorEngine.evaluatePartial("(1500 - 300")
        assertNotNull(result)
        assertEquals("1200", CalculatorEngine.formatResult(result!!))
    }

    @Test
    fun formatWholeAndDecimalNumbers() {
        assertEquals("500", CalculatorEngine.formatResult(BigDecimal("500.00")))
        assertEquals("500.5", CalculatorEngine.formatResult(BigDecimal("500.50")))
        assertEquals("12.75", CalculatorEngine.formatResult(BigDecimal("12.75")))
        assertEquals("0", CalculatorEngine.formatResult(BigDecimal("0.00")))
    }
}

