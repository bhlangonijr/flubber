package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.action.Action
import com.github.bhlangonijr.flubber.action.JavascriptAction
import com.github.bhlangonijr.flubber.action.PythonAction
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.script.ScriptException
import com.github.bhlangonijr.flubber.util.Util.Companion.loadResource
import com.github.bhlangonijr.flubber.util.Util.Companion.objectToNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class FlowEngineTest {

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

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }
        script.register("waitOnDigits") {
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

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("say", PythonAction(sayAction))
        script.register("hangup") {
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

        val queue = ArrayBlockingQueue<Exception>(2)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("say", PythonAction(sayAction))

        engine.run { script.with(args) }.onException {
            queue.offer(it as Exception)
        }

        assertTrue(queue.poll(5, TimeUnit.SECONDS) is ScriptException)
    }

    @Test
    fun `test resuming script with callback`() {

        val queue = ArrayBlockingQueue<String>(3)
        val queueRequest = ArrayBlockingQueue<JsonNode>(2)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }
        script.register(
            "waitOnDigits", JavascriptAction(
                """
        var action = function(context, args) {
            var result = {
              "callback": true,
              "threadId": args.threadId
            }
            return result;
        }   
        """.trimIndent()
            )
        )

        val context = script.with(args)
        engine.run { context }.onAction { node, _, result ->
            if (node["action"]?.asText() == "waitOnDigits") {
                queueRequest.offer(objectToNode(result!!))
            }
        }

        queueRequest.poll(5, TimeUnit.SECONDS)?.let {
            //fake external service response
            engine.callback(
                context, Callback.from(
                    """ 
                {
                  "threadId": "${it["threadId"].asText()}",
                  "result": "1000"
                }
            """.trimIndent()
                )
            )
                .onException { e -> e.printStackTrace() }
        }

        assertEquals("hello john, press 1000 to greet or 2000 to quit.", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("have a good one john", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("bye john, returned from decision", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test script hooks`() {

        val queue = ArrayBlockingQueue<String>(3)
        val queueRequest = ArrayBlockingQueue<JsonNode>(2)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }
        script.register(
            "waitOnDigits", JavascriptAction(
                """
        var action = function(context, args) {
            var result = {
              "callback": true,
              "threadId": args.threadId
            }
            return result;
        }   
        """.trimIndent()
            )
        )

        val context = script.with(args)
        engine.run { context }.onAction { node, _, result ->
            if (node["action"]?.asText() == "waitOnDigits") {
                queueRequest.offer(objectToNode(result!!))
            }
        }

        queueRequest.poll(5, TimeUnit.SECONDS)?.let {
            //fake external service to call a hook
            engine.hook(context, Event.from("""
                {
                  "event": "hangup",
                  "args": {
                    "code": "external quit"
                  }
                }
            """.trimIndent()))
                .onException { e -> e.printStackTrace() }
        }

        assertEquals("hello john, press 1000 to greet or 2000 to quit.", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("exited john with external quit", queue.poll(5, TimeUnit.SECONDS))
    }
}