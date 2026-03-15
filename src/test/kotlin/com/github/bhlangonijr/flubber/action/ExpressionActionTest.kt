package com.github.bhlangonijr.flubber.action

import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExpressionActionTest {

    private val context = makeJson()
    private val expression = ExpressionAction()

    @Test
    fun `test set and run simple conditions`() {

        val decision = ExpressionAction()

        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "1 == 1"))) as Boolean)
        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "10/2 == 20/4"))) as Boolean)
        assertFalse(decision.execute(context, mutableMapOf(Pair("condition", "10/5 == 20/4"))) as Boolean)
        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "3 * (5/2) > 1/4"))) as Boolean)
        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "\"test\" == \"test\""))) as Boolean)
        assertTrue(
            decision.execute(
                context,
                mutableMapOf(Pair("condition", "\"testing\".length() > \"test\".length()"))
            ) as Boolean
        )
    }

    @Test
    fun `test set and run simple evaluations`() {

        val expression = ExpressionAction()

        assertEquals("TEST", expression.execute(context, mutableMapOf(Pair("text", "\"TEST_REMOVE\".substring(0, 4)"))))
        assertEquals(
            "TEST_REPLACE", expression.execute(
                context,
                mutableMapOf(Pair("text", "\"TEST_REMOVE\".replace(\"REMOVE\", \"REPLACE\")"))
            )
        )
    }

    @Test
    fun `test set and run conditions with args`() {

        val decision = ExpressionAction()

        assertTrue(
            decision.execute(
                context, mutableMapOf(
                    Pair("first", 1), Pair("second", 2), Pair("condition", "args.first < args.second")
                )
            ) as Boolean
        )
        assertFalse(
            decision.execute(
                context, mutableMapOf(
                    Pair("first", 1), Pair("second", 2), Pair("condition", "args.first >= args.second")
                )
            ) as Boolean
        )
        assertTrue(
            decision.execute(
                context, mutableMapOf(
                    Pair("first", "test"), Pair("second", "test"), Pair("condition", "args.first == args.second")
                )
            ) as Boolean
        )
    }

    @Test
    fun `test args with JSON-like string values are treated as data not code`() {
        assertTrue(
            expression.execute(
                context, mutableMapOf(
                    Pair("value", """{"key": "value", "nested": [1,2,3]}"""),
                    Pair("condition", "args.value.indexOf('{') === 0")
                )
            ) as Boolean
        )
        assertTrue(
            expression.execute(
                context, mutableMapOf(
                    Pair("value", """[{"a":1},{"b":2}]"""),
                    Pair("condition", "typeof args.value === 'string'")
                )
            ) as Boolean
        )
    }

    @Test
    fun `test args with special JS characters are not interpreted as code`() {
        assertTrue(
            expression.execute(
                context, mutableMapOf(
                    Pair("value", "); process.exit(1); //"),
                    Pair("condition", "typeof args.value === 'string'")
                )
            ) as Boolean
        )
        assertTrue(
            expression.execute(
                context, mutableMapOf(
                    Pair("value", "` + (function(){ return 'injected' })() + `"),
                    Pair("condition", "args.value.length > 0")
                )
            ) as Boolean
        )
        assertEquals(
            "hello; drop table;", expression.execute(
                context, mutableMapOf(Pair("data", "hello; drop table;"), Pair("text", "args.data"))
            )
        )
    }

    @Test
    fun `test args with null values`() {
        assertTrue(
            expression.execute(
                context, mutableMapOf(
                    Pair("value", null),
                    Pair("condition", "args.value == null")
                )
            ) as Boolean
        )
    }

    @Test
    fun `test args with nested map values`() {
        val nested = mutableMapOf<String, Any?>(
            "inner" to "hello",
            "count" to 42
        )
        assertTrue(
            expression.execute(
                context, mutableMapOf(
                    Pair("data", nested),
                    Pair("condition", "args.data.inner === 'hello' && args.data.count === 42")
                )
            ) as Boolean
        )
    }

    @Test
    fun `test invalid expression throws exception`() {
        assertThrows<Exception> {
            expression.execute(context, mutableMapOf(Pair("condition", "{{{")))
        }
    }

    @Test
    fun `test bindings do not leak between executions`() {
        expression.execute(
            context, mutableMapOf(
                Pair("secret", "sensitive_data"),
                Pair("text", "args.secret")
            )
        )
        assertTrue(
            expression.execute(
                context, mutableMapOf(
                    Pair("condition", "typeof secret === 'undefined'")
                )
            ) as Boolean
        )
    }
}