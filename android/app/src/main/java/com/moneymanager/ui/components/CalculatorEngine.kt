package com.moneymanager.ui.components

import java.math.BigDecimal
import java.math.RoundingMode

object CalculatorEngine {

    private sealed interface Token {
        data class Number(val value: BigDecimal) : Token
        data object Plus : Token
        data object Minus : Token
        data object Multiply : Token
        data object Divide : Token
        data object LParen : Token
        data object RParen : Token
    }

    /**
     * Evaluates a complete math expression using standard operator precedence (BODMAS).
     * Returns null if the expression is invalid or encounters division by zero.
     */
    fun evaluate(expression: String): BigDecimal? {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return null
        val tokens = tokenize(trimmed) ?: return null
        if (tokens.isEmpty()) return null
        return try {
            val parser = Parser(tokens)
            parser.parse()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates a partial expression (e.g. while the user is still typing, with trailing
     * operators or unclosed parentheses).
     */
    fun evaluatePartial(expression: String): BigDecimal? {
        val prepared = prepareForEvaluation(expression)
        return evaluate(prepared)
    }

    /**
     * Prepares an incomplete expression for evaluation by stripping trailing operators
     * and closing unclosed parentheses.
     */
    fun prepareForEvaluation(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return "0"

        // Strip trailing operators, open parens, or decimal points
        while (s.isNotEmpty()) {
            val last = s.last()
            if (last in "+-*/−×÷." || last.isWhitespace() || last == '(') {
                s = s.dropLast(1).trimEnd()
            } else {
                break
            }
        }
        if (s.isEmpty()) return "0"

        // Balance unclosed parentheses
        val openCount = s.count { it == '(' }
        val closeCount = s.count { it == ')' }
        if (openCount > closeCount) {
            s += ")".repeat(openCount - closeCount)
        }
        return s
    }

    /**
     * Formats a BigDecimal result for display or input insertion.
     * Whole numbers return without decimal places (e.g. "500").
     * Decimals round to 2 places (e.g. "33.33", "12.5").
     */
    fun formatResult(value: BigDecimal): String {
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        return if (rounded.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            rounded.toBigInteger().toString()
        } else {
            rounded.stripTrailingZeros().toPlainString()
        }
    }

    private fun tokenize(input: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        val s = input.replace('−', '-').replace('×', '*').replace('÷', '/')
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '+' -> { tokens.add(Token.Plus); i++ }
                c == '-' -> { tokens.add(Token.Minus); i++ }
                c == '*' -> { tokens.add(Token.Multiply); i++ }
                c == '/' -> { tokens.add(Token.Divide); i++ }
                c == '(' -> { tokens.add(Token.LParen); i++ }
                c == ')' -> { tokens.add(Token.RParen); i++ }
                c.isDigit() || c == '.' -> {
                    val start = i
                    var hasDot = c == '.'
                    i++
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                        if (s[i] == '.') {
                            if (hasDot) return null
                            hasDot = true
                        }
                        i++
                    }
                    val numStr = s.substring(start, i)
                    val bd = numStr.toBigDecimalOrNull() ?: return null
                    tokens.add(Token.Number(bd))
                }
                else -> return null
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        private fun peek(): Token? = if (pos < tokens.size) tokens[pos] else null
        private fun consume(): Token = tokens[pos++]

        fun parse(): BigDecimal {
            val result = parseExpression()
            if (pos < tokens.size) {
                throw IllegalArgumentException("Unexpected token at position $pos")
            }
            return result
        }

        private fun parseExpression(): BigDecimal {
            var left = parseTerm()
            while (true) {
                when (peek()) {
                    Token.Plus -> {
                        consume()
                        val right = parseTerm()
                        left = left.add(right)
                    }
                    Token.Minus -> {
                        consume()
                        val right = parseTerm()
                        left = left.subtract(right)
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseTerm(): BigDecimal {
            var left = parseFactor()
            while (true) {
                when (peek()) {
                    Token.Multiply -> {
                        consume()
                        val right = parseFactor()
                        left = left.multiply(right)
                    }
                    Token.Divide -> {
                        consume()
                        val right = parseFactor()
                        if (right.compareTo(BigDecimal.ZERO) == 0) {
                            throw ArithmeticException("Division by zero")
                        }
                        left = left.divide(right, 8, RoundingMode.HALF_UP)
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseFactor(): BigDecimal {
            val current = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
            return when (current) {
                Token.Minus -> {
                    consume()
                    parseFactor().negate()
                }
                Token.Plus -> {
                    consume()
                    parseFactor()
                }
                Token.LParen -> {
                    consume()
                    val result = parseExpression()
                    if (peek() != Token.RParen) {
                        throw IllegalArgumentException("Missing closing parenthesis")
                    }
                    consume()
                    result
                }
                is Token.Number -> {
                    consume()
                    current.value
                }
                else -> throw IllegalArgumentException("Unexpected token: $current")
            }
        }
    }
}

