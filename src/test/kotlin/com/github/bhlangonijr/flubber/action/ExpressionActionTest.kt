package com.github.bhlangonijr.flubber.action

import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpressionActionTest {

    private val context = makeJson()

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
}