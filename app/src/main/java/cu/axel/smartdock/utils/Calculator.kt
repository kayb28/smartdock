package cu.axel.smartdock.utils

/**
 * Recursive-descent parser behind the app-menu calculator, extracted as a
 * pure-JVM object so it can be unit-tested without android.jar.
 * [cu.axel.smartdock.utils.Utils.solve] delegates here.
 *
 * Methods of one object may mutually recurse (Kotlin local functions cannot
 * forward-reference, which is why this is not nested inside solve()).
 * Grammar: expr := term (('+'|'-') term)* ; term := power (('*'|'/') power)* ;
 * power := primary ('^' power)? ; primary := number | '(' expr ')' | sign primary.
 *
 * A malformed expression (unknown character, dangling operator, unbalanced
 * parentheses) never throws: tokenize rejects unknown characters by returning
 * an empty list, the parser returns 0.0 when it runs out of tokens or meets a
 * token it cannot read as a number, and evaluate collapses NaN/Infinite to 0.0.
 */
object Calculator {
    fun solve(expression: String): Double = evaluate(tokenize(expression))

    fun evaluate(tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0
        val parser = Parser(tokens)
        val result = parser.expr()
        return if (result.isNaN() || result.isInfinite()) 0.0 else result
    }

    private fun tokenize(expression: String): List<String> {
        val tokens = mutableListOf<String>()
        val number = StringBuilder()
        fun flushNumber() {
            if (number.isNotEmpty()) {
                tokens.add(number.toString())
                number.setLength(0)
            }
        }
        for (c in expression) {
            when {
                c.isDigit() || c == '.' -> number.append(c)
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '(' || c == ')' -> {
                    flushNumber()
                    tokens.add(c.toString())
                }
                c.isWhitespace() -> flushNumber()
                else -> return emptyList() // reject anything the grammar doesn't know
            }
        }
        flushNumber()
        return tokens
    }

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        private fun next(): String = tokens[pos++]

        fun expr(): Double {
            var value = term()
            while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
                val op = next()
                val rhs = term()
                value = if (op == "+") value + rhs else value - rhs
            }
            return value
        }

        private fun term(): Double {
            var value = power()
            while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
                val op = next()
                val rhs = power()
                value = if (op == "*") value * rhs else value / rhs
            }
            return value
        }

        // a^b binds tightest, right-associative: 2^3^2 = 2^(3^2).
        private fun power(): Double {
            val base = primary()
            if (pos < tokens.size && tokens[pos] == "^") {
                next()
                val exponent = power()
                return Math.pow(base, exponent)
            }
            return base
        }

        private fun primary(): Double {
            if (pos >= tokens.size) return 0.0
            val token = next()
            if (token == "(") {
                val value = expr()
                if (pos < tokens.size && tokens[pos] == ")") pos++
                return value
            }
            if (token == "-") return -primary()
            if (token == "+") return primary()
            return token.toDoubleOrNull() ?: 0.0
        }
    }
}
