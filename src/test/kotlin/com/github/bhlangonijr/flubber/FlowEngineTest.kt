package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.action.Action
import com.github.bhlangonijr.flubber.action.JavascriptAction
import com.github.bhlangonijr.flubber.action.PythonAction
import com.github.bhlangonijr.flubber.context.Context
import com.github.bhlangonijr.flubber.context.ExecutionState
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.script.ScriptException
import com.github.bhlangonijr.flubber.util.Util.Companion.loadResource
import com.github.bhlangonijr.flubber.util.Util.Companion.objectToNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import kotlin.random.Random

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
    fun `test running full script`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(10)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }
        script.register("waitOnDigits") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    val input = "1000"
                    args["set"]?.let { context.put(it as String, input) }
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
    fun `test running script with unconditional block`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(3)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example-run.json"))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any? {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }
        engine.run { script.with(args) }

        assertEquals("hello john.", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("have a good one john doe", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("bye john, returned from run", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test running script and respond to listeners`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(3)
        val queueRequest = ArrayBlockingQueue<JsonNode>(2)
        val queueState = ArrayBlockingQueue<ExecutionState>(5)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example-async.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
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
        context.onAction { node, _, result ->
            if (node["action"]?.asText() == "waitOnDigits") {
                queueRequest.offer(objectToNode(result!!))
            }
        }.onStateChange { _, state ->
            queueState.offer(state)
        }
        engine.run { context }

        queueRequest.poll(5, TimeUnit.SECONDS)?.let {
            //fake external service response
            engine.run(
                context, Callback.from(
                    """ 
                {
                  "threadId": "${it["threadId"].asText()}",
                  "result": "1000"
                }
            """.trimIndent()
                )
            ).onException { e -> e.printStackTrace() }
        }
        (1..3).forEach { _ -> queue.poll(5, TimeUnit.SECONDS) }
        assertEquals(ExecutionState.RUNNING, queueState.poll(5, TimeUnit.SECONDS))
        assertEquals(ExecutionState.WAITING, queueState.poll(5, TimeUnit.SECONDS))
        assertEquals(ExecutionState.FINISHED, queueState.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test missing actions in main flow`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(2)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("say", PythonAction(sayAction))
        script.register("hangup") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    queue.offer(args["reference"] as String)
                    return "ok"
                }
            }
        }

        engine.run { script.with(args) }
        assertEquals("initial.answer", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test missing registered action inside exception handling block`() = runBlocking {

        val queue = ArrayBlockingQueue<Exception>(2)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("say", PythonAction(sayAction))
        val context = script.with(args)
        context
            .onException {
                queue.offer(it as Exception)
            }
        engine.run { context }

        assertTrue(queue.poll(5, TimeUnit.SECONDS) is ScriptException)
    }

    @Test
    fun `test resuming script with callback`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(3)
        val queueRequest = ArrayBlockingQueue<JsonNode>(2)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example-async.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
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
        context.onAction { node, _, result ->
            if (node["action"]?.asText() == "waitOnDigits") {
                queueRequest.offer(objectToNode(result!!))
            }
        }
        engine.run { context }

        queueRequest.poll(5, TimeUnit.SECONDS)?.let {
            //fake external service response
            engine.run(
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

        assertEquals("hello john doe, press 1000 to greet or 2000 to quit.", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("have a good one john doe", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("bye john, returned from decision", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test script hooks`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(3)
        val queueRequest = ArrayBlockingQueue<ObjectNode>(2)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example-async.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
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
        context.onAction { node, _, result ->
            if (node["action"]?.asText() == "waitOnDigits") {
                queueRequest.offer(objectToNode(result!!) as ObjectNode)
            }
        }
        engine.run { context }

        queueRequest.poll(5, TimeUnit.SECONDS)?.let {
            //fake external service to call a hook
            engine.run(
                context, Event.from(
                    """
                {
                  "event": "hangup",
                  "args": {
                    "code": "external quit"
                  }
                }
            """.trimIndent()
                )
            ).onException { e -> e.printStackTrace() }
        }

        assertEquals("hello john doe, press 1000 to greet or 2000 to quit.", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("exited john with external quit", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test sequence iterations within the flow`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(5)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example-iterate.json"))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }

        engine.run { script.with(args) }
        assertEquals("have a good one JOHN Doe", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("have a good one MARY Doe", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("have a good one ALICE Doe", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("returned from iterations", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test sequence call from menu`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(5)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example-menu.json"))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }

        engine.run { script.with(args) }
        assertEquals("hello john", queue.poll(5, TimeUnit.SECONDS))
        assertEquals("returned from menu", queue.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test sequence parallel iterations within the flow`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(10)
        val engine = FlowEngine()

        val script = Script.from(loadResource("/script-example-iterate-parallel.json"))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    if (args["threadId"] == "mainThreadId") {
                        Thread.sleep(Random.nextLong(100, 700))
                    }
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }

        engine.run { script.with(args) }
        val messages = mutableListOf<String>()
        var messageCounter = 0
        for (x in 1..6) {
            queue.poll(5, TimeUnit.SECONDS)?.let {
                messages.add(it)
                messageCounter++
            }
        }
        assertTrue(messages.contains("have a good one JOHN Doe"))
        assertTrue(messages.contains("have a good one MARY Doe"))
        assertTrue(messages.contains("have a good one ALICE Doe"))
        assertTrue(messages.contains("have a good one ROMEO Doe"))
        assertTrue(messages.contains("have a good one JASON Doe"))
        assertTrue(messages.any { it.startsWith("returned from iterations, first name: JOHN Doe and last name") })
        assertEquals(6, messageCounter)
    }

    @Test
    @Disabled
    fun `test sequence parallel iterations within the flow - concurrency`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(300)
        val engine = FlowEngine()
        val concurrency = 40

        val script = Script.from(loadResource("/script-example-iterate-parallel.json"))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    if (args["threadId"] == "mainThreadId") {
                        Thread.sleep(Random.nextLong(100, 700))
                    }
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }

        val executor = Executors.newFixedThreadPool(concurrency)

        repeat((1..concurrency).count()) { _ ->
            executor.submit {
                runBlocking {
                    engine.run { script.with(args) }
                }
            }
        }

        val messages = mutableListOf<String>()
        var messageCounter = 0
        for (x in 1..6 * concurrency) {
            queue.poll(5, TimeUnit.SECONDS)?.let {
                messages.add(it)
                messageCounter++
            }
        }
        assertTrue(messages.contains("have a good one JOHN Doe"))
        assertTrue(messages.contains("have a good one MARY Doe"))
        assertTrue(messages.contains("have a good one ALICE Doe"))
        assertTrue(messages.contains("have a good one ROMEO Doe"))
        assertTrue(messages.contains("have a good one JASON Doe"))
        assertTrue(messages.any { it.startsWith("returned from iterations, first name: JOHN Doe and last name") })
        assertEquals(6 * concurrency, messageCounter)
    }

    @Test
    //@Disabled
    fun `test async sequence iterations within the flow - concurrency`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(1200)
        val queueRequest = mutableMapOf<String, ArrayBlockingQueue<JsonNode>>()
        val engine = FlowEngine()
        val concurrency = 200

        val init = System.currentTimeMillis()
        val script = Script.from(loadResource("/script-example-async.json"))
        script.register("answer", JavascriptAction(answerAction))
        script.register("hangup", JavascriptAction(hangupAction))
        script.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    queue.offer(args["text"] as String)
                    return "ok"
                }
            }
        }
        script.register("waitOnDigits", JavascriptAction(
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

        val executor = Executors.newFixedThreadPool(concurrency)
        val contexts = mutableListOf<Context>()
        repeat((1..concurrency).count()) { _ ->
            val context = script.with(args)
            queueRequest[context.id] = ArrayBlockingQueue<JsonNode>(1200)
            executor.submit {
                runBlocking {
                    context.onAction { node, _, result ->
                        if (node["action"]?.asText() == "waitOnDigits") {
                            queueRequest[context.id]?.offer(objectToNode(result!!))
                        }
                    }
                    engine.run { context }
                }
            }
            contexts.add(context)
        }

        contexts.forEach { context ->
            queueRequest[context.id]?.poll(30, TimeUnit.SECONDS)?.let {
                //fake external service response
                engine.run(
                    context, Callback.from(""" 
                                {
                                  "threadId": "${it["threadId"].asText()}",
                                  "result": "1000"
                                }
                            """.trimIndent())
                )
                    .onException { e -> e.printStackTrace() }
            }
        }

        val messages = mutableListOf<String>()
        var messageCounter = 0
        for (x in 1..3 * concurrency) {
            queue.poll(10, TimeUnit.SECONDS)?.let {
                messages.add(it)
                messageCounter++
                it
            } ?: break
        }
        println("Total time: ${System.currentTimeMillis() - init}")
        assertTrue(messages.contains("hello john doe, press 1000 to greet or 2000 to quit."))
        assertTrue(messages.contains("have a good one john doe"))
        assertTrue(messages.contains("bye john, returned from decision"))
        assertEquals(3 * concurrency, messageCounter)
    }
}