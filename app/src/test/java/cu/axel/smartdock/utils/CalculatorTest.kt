package cu.axel.smartdock.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the app-menu calculator parser, extracted as a pure-JVM
 * object so it runs under `./gradlew test` without android.jar. Covers the
 * cases the task names: basic ops, precedence, syntax errors, empty input,
 * decimals and large numbers. The parser never throws on bad input — it
 * returns 0.0 — so several "syntax error" cases assert a deterministic 0
 * rather than an exception.
 */
class CalculatorTest {

    // --- basic operations ---

    @Test fun addition() = assertEquals(5.0, Calculator.solve("2+3"), 0.0)

    @Test fun subtraction() = assertEquals(6.0, Calculator.solve("10-4"), 0.0)

    @Test fun multiplication() = assertEquals(42.0, Calculator.solve("6*7"), 0.0)

    @Test fun division() = assertEquals(5.0, Calculator.solve("20/4"), 0.0)

    @Test fun power() = assertEquals(8.0, Calculator.solve("2^3"), 0.0)

    // --- precedence and associativity ---

    /** The regression that motivated the full parser: first-pair-only math
     *  turned "2+3+4" into 5. */
    @Test fun leftAssociativeAdditionChain() = assertEquals(9.0, Calculator.solve("2+3+4"), 0.0)

    @Test fun multiplicationBeforeAddition() = assertEquals(14.0, Calculator.solve("2+3*4"), 0.0)

    @Test fun multiplicationBeforeAdditionReversed() =
        assertEquals(10.0, Calculator.solve("2*3+4"), 0.0)

    @Test fun subtractionLeftAssociative() = assertEquals(2.0, Calculator.solve("8-4-2"), 0.0)

    @Test fun divisionLeftAssociative() = assertEquals(1.0, Calculator.solve("8/4/2"), 0.0)

    /** power is right-associative: 2^3^2 = 2^(3^2) = 2^9 = 512. */
    @Test fun powerRightAssociative() = assertEquals(512.0, Calculator.solve("2^3^2"), 0.0)

    @Test fun powerBindsTighterThanTerm() = assertEquals(17.0, Calculator.solve("2^3+3^2"), 0.0)

    // --- parentheses ---

    @Test fun parensOverridePrecedence() = assertEquals(20.0, Calculator.solve("(2+3)*4"), 0.0)

    @Test fun parensAfterOperator() = assertEquals(14.0, Calculator.solve("2*(3+4)"), 0.0)

    @Test fun nestedParens() = assertEquals(3.0, Calculator.solve("((1+2))"), 0.0)

    @Test fun parensWithInnerExpression() = assertEquals(7.0, Calculator.solve("(1+2)*(2+1)-2"), 0.0)

    // --- unary minus / plus ---

    @Test fun unaryMinus() = assertEquals(-5.0, Calculator.solve("-5"), 0.0)

    @Test fun unaryMinusAfterOperator() = assertEquals(-1.0, Calculator.solve("2+-3"), 0.0)

    @Test fun unaryMinusInMultiplication() = assertEquals(-6.0, Calculator.solve("2*-3"), 0.0)

    @Test fun doubleUnaryMinus() = assertEquals(5.0, Calculator.solve("--5"), 0.0)

    // --- empty input ---

    @Test fun emptyStringReturnsZero() = assertEquals(0.0, Calculator.solve(""), 0.0)

    @Test fun whitespaceOnlyReturnsZero() = assertEquals(0.0, Calculator.solve("   "), 0.0)

    // --- syntax errors: never throw, deterministic result ---

    @Test fun unknownCharacterReturnsZero() = assertEquals(0.0, Calculator.solve("abc"), 0.0)

    @Test fun divisionByZeroCollapsesToZero() = assertEquals(0.0, Calculator.solve("3/0"), 0.0)

    @Test fun loneOperatorReturnsZero() = assertEquals(0.0, Calculator.solve("+"), 0.0)

    @Test fun emptyParensReturnZero() = assertEquals(0.0, Calculator.solve("()"), 0.0)

    @Test fun unknownCharMixedWithValidReturnsZero() =
        assertEquals(0.0, Calculator.solve("2&3"), 0.0)

    @Test fun solveNeverThrowsOnGarbage() {
        // A grab-bag of malformed input: each must return a Double, not throw.
        val junk = listOf("2++3", "2+", "(2+3", "2**3", "^3", "()", "1.2.3", "(((")
        for (e in junk) {
            val result = Calculator.solve(e)
            assertTrue("solve(\"$e\") must be finite", !result.isNaN() && !result.isInfinite())
        }
    }

    // --- decimals ---

    @Test fun decimalAddition() = assertEquals(0.75, Calculator.solve("0.5+0.25"), 1e-9)

    @Test fun decimalMultiplication() = assertEquals(5.0, Calculator.solve("2.5*2"), 0.0)

    @Test fun nonTerminatingDivision() = assertEquals(10.0 / 3.0, Calculator.solve("10/3"), 1e-9)

    // --- large numbers ---

    @Test fun largeProduct() = assertEquals(1e12, Calculator.solve("1000000*1000000"), 0.0)

    @Test fun largeLiteral() =
        assertEquals(999999999999.0, Calculator.solve("999999999999"), 0.0)

    // --- evaluate on token lists directly ---

    @Test fun evaluateOnTokenList() =
        assertEquals(5.0, Calculator.evaluate(listOf("2", "+", "3")), 0.0)

    @Test fun evaluateOnEmptyListReturnsZero() = assertEquals(0.0, Calculator.evaluate(emptyList()), 0.0)

    @Test fun solveDelegatesToEvaluateConsistently() {
        // solve(expression) must equal evaluate(tokenize(expression)) for a few cases.
        for (e in listOf("2+3*4", "(1+2)^2", "10/4", "-5+10")) {
            assertEquals(Calculator.evaluate(tokensOf(e)), Calculator.solve(e), 0.0)
        }
    }

    private fun tokensOf(expression: String): List<String> {
        // Mirror the private tokenizer's behavior for the consistency check:
        // digits and '.' build a number; operators and parens are single tokens;
        // whitespace flushes; anything else yields empty.
        val tokens = mutableListOf<String>()
        val number = StringBuilder()
        fun flush() {
            if (number.isNotEmpty()) {
                tokens.add(number.toString()); number.setLength(0)
            }
        }
        for (c in expression) when {
            c.isDigit() || c == '.' -> number.append(c)
            c in "+-*/^()" -> { flush(); tokens.add(c.toString()) }
            c.isWhitespace() -> flush()
            else -> return emptyList()
        }
        flush()
        return tokens
    }
}
