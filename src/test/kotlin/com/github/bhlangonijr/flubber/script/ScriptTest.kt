package com.github.bhlangonijr.flubber.script

import com.github.bhlangonijr.flubber.util.Util.Companion.loadResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScriptTest {

    @Test
    fun `test script loading`() {

        val script = Script.from(loadResource("/script-example.json"))

        assertEquals(4, script.flow()?.size())
        assertEquals(2, script.sequence("greetAndExit")?.size())
        assertEquals("hangup", script.action("greetAndExit", 1)?.get("action")?.asText())
    }
}