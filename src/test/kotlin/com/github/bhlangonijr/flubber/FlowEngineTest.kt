package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.action.Action
import com.github.bhlangonijr.flubber.action.JavascriptAction
import com.github.bhlangonijr.flubber.action.PythonAction
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.script.ScriptException
import com.github.bhlangonijr.flubber.util.Util.Companion.loadResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class FlowEngineTest {

    private val script = Script.from(loadResource("/script-example.json"))
    private val answerAction = loadResource("/actions/answer.js")
    private val hangupAction = loadResource("/actions/hangup.js")
    private val sayAction = loadResource("/actions/say.py")

    val args = """
            {
              "session":{
              "user":"john"
              }
            }
        """

    @Test
    fun `test running full script`() {

        val queue = ArrayBlockingQueue<String>(3)
        val engine = FlowEngine()

        engine.register("answer", JavascriptAction(answerAction))
        engine.register("hangup", JavascriptAction(hangupAction))
        engine.register("say") {
            object : Action {
                override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }
        engine.register("waitOnDigits") {
            object : Action {
                override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                    val input = "1000"
                    args["set"]?.let { (context as ObjectNode).put(it as String, input) }
                    return input
                }
            }
        }

        engine.run { script.with(args) }

        assertEquals("hello john, press 1000 to greet or 2000 to quit.", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("have a good one john", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("bye john, returned from decision", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test missing actions in main flow`() {

        val queue = ArrayBlockingQueue<String>(2)
        val engine = FlowEngine()

        engine.register("answer", JavascriptAction(answerAction))
        engine.register("say", PythonAction(sayAction))
        engine.register("hangup") {
            object : Action {
                override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                    queue.offer(args["reference"] as String)
                    return "ok"
                }
            }
        }

        engine.run { script.with(args) }
        assertEquals("initial.answer", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test missing registered action inside exception handling block`() {

        val engine = FlowEngine()

        engine.register("answer", JavascriptAction(answerAction))
        engine.register("say", PythonAction(sayAction))

        assertThrows<ScriptException> {
            engine.run { script.with(args) }
        }
    }

}