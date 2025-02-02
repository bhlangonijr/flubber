package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PythonActionTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `test set and run script`() {

        val script = """
def action(context, args):
    # do stuff
    context.put("action", "done")
    return "Hello " + args["arg1"]
        """
        val context = mapper.readTree("{}") as ObjectNode
        val result = PythonAction(script)
            .execute(context, mutableMapOf(Pair("arg1", "world")))

        assertTrue(context.has("action"))
        assertEquals("Hello world", result)
    }
}