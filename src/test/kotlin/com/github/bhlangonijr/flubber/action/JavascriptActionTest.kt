package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavascriptActionTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `test set and run script`() {

        val script = """
            var action = function(context, args) {
                // do stuff
                context.put("action", "OK")
                return "Hello " + args.arg1;
            }
        """
        val context = mapper.readTree("{}")
        val result = JavascriptAction(script)
            .execute(context, mutableMapOf(Pair("arg1", "world")))

        assertTrue(context.has("action"))
        assertEquals("Hello world", result)
    }
}