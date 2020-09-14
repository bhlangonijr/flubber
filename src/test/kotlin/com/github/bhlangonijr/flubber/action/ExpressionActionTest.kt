package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpressionActionTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `test set and run simple conditions`() {

        val context = mapper.readTree("{}") as ObjectNode
        val decision = ExpressionAction()

        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "1 == 1"))))
        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "10/2 == 20/4"))))
        assertFalse(decision.execute(context, mutableMapOf(Pair("condition", "10/5 == 20/4"))))
        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "3 * (5/2) > 1/4"))))
        assertTrue(decision.execute(context, mutableMapOf(Pair("condition", "\"test\" == \"test\""))))
        assertTrue(
            decision.execute(
                context,
                mutableMapOf(Pair("condition", "\"testing\".length() > \"test\".length()"))
            )
        )
    }

    @Test
    fun `test set and run conditions with args`() {

        val context = mapper.readTree("{}") as ObjectNode
        val decision = ExpressionAction()

        assertTrue(
            decision.execute(
                context, mutableMapOf(
                    Pair("first", 1), Pair("second", 2), Pair("condition", "args.first < args.second")
                )
            )
        )
        assertFalse(
            decision.execute(
                context, mutableMapOf(
                    Pair("first", 1), Pair("second", 2), Pair("condition", "args.first >= args.second")
                )
            )
        )
        assertTrue(
            decision.execute(
                context, mutableMapOf(
                    Pair("first", "test"), Pair("second", "test"), Pair("condition", "args.first == args.second")
                )
            )
        )
    }
}