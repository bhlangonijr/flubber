package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.util.Util.Companion.objectToNode
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

    @Test
    fun `test return json`() {

        val script = """
            var action = function(context, args) {
                var result = {"exit": true}
                return result;
            }
        """
        val context = mapper.readTree("{}")
        val result = JavascriptAction(script)
            .execute(context, mutableMapOf(Pair("arg1", "world")))

        assertTrue(objectToNode(result!!).get(Script.EXIT_NODE_FIELD_NAME)?.asBoolean() == true)
    }
}