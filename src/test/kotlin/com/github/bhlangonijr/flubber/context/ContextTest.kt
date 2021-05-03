package com.github.bhlangonijr.flubber.context

import com.github.bhlangonijr.flubber.context.Context.Companion.MAIN_THREAD_ID
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.util.Util.Companion.loadResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContextTest {

    @Test
    fun `test context creation`() {

        val args = """ 
        {
          "session": {
            "user": "ben-hur"      
          } 
        }
        """.trimIndent()

        val script = Script.from(loadResource("/script-example.json"))
        val context = Context.create(script, args)

        assertEquals("ben-hur", context.globalArgs["session"]["user"].asText())
        assertEquals("answer", script.action("main", 0)?.get("action")?.asText())
        assertEquals(ExecutionState.NEW, context.threadStateValue(MAIN_THREAD_ID))
    }

    @Test
    fun `test context loading`() {

        val context = Context.load(loadResource("/context-sample.json"))
        val script = context.script

        assertEquals("ben-hur", context.globalArgs["session"]["user"].asText())
        assertEquals("answer", script.action("main", 0)?.get("action")?.asText())
        assertEquals(ExecutionState.NEW, context.threadStateValue(MAIN_THREAD_ID))
    }

    @Test
    fun `test next action and stack push and pop`() {

        val threadId = "main"
        val args = """
        {
          "session": {
            "user": "ben-hur"
          }
        }
        """.trimIndent()

        val script = Script.from(loadResource("/script-example.json"))
        val context = Context.create(script, args)

        context.push(threadId, StackFrame.create("main", 0))
        context.push(threadId, StackFrame.create("main", 1))

        assertEquals(2, context.threadStack(threadId).size())
        assertEquals(1, context.pop(threadId)?.actionIndex)
        assertEquals(0, context.pop(threadId)?.actionIndex)
    }

    @Test
    fun `test action iteration`() {

        val threadId = MAIN_THREAD_ID
        val args = """
        {
          "session": {
            "user": "ben-hur"
          }
        }
        """.trimIndent()

        val script = Script.from(loadResource("/script-example.json"))
        val context = Context.create(script, args)

        var frame = context.next()
        context.push(threadId, StackFrame.create(frame!!.sequenceId, frame.actionIndex))
        assertEquals("answer", frame.node["action"]!!.asText())
        frame = context.next()
        context.push(threadId, StackFrame.create(frame!!.sequenceId, frame.actionIndex))
        assertEquals("say", frame.node["action"]!!.asText())
        frame = context.next()
        context.push(threadId, StackFrame.create(frame!!.sequenceId, frame.actionIndex))
        assertEquals("waitOnDigits", frame.node["action"]!!.asText())
        assertEquals(ExecutionState.RUNNING, context.threadStateValue(MAIN_THREAD_ID))
    }
}