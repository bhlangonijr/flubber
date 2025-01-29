package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.action.Action
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.util.Util.Companion.loadResource
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowEngineIntegrationTest {

    private val scriptWithImports = Script.from(loadResource("/script-example-import.json"))
    private val scriptWithRest = Script.from(loadResource("/script-example-rest.json"))

    @Test
    fun `test imported actions`() = runBlocking {

        val queue = ArrayBlockingQueue<Boolean>(2)

        val args = """
            {
              "session":{
                "user":"john"
              }
            }
        """

        scriptWithImports.register("waitOnDigits") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    val input = "1000"
                    args["set"]?.let { context.put(it as String, input) }
                    args["set"]?.let { context.put("COMPLETED", true) }
                    return input
                }
            }
        }

        scriptWithImports
            .with(args)
            .apply {
                this.onComplete { queue.offer(this.globalArgs.get("COMPLETED").asBoolean()) }
            }
            .run()

        assertTrue(withContext(Dispatchers.IO) {
            queue.poll(5, TimeUnit.SECONDS)
        } == true)
    }

    @Test
    fun `test rest actions`() = runBlocking {

        val queue = ArrayBlockingQueue<String>(2)

        val args = """
            {
              "session":{
                "user":"john",
                "url":"https://my-json-server.typicode.com/typicode/demo/profile"
              }
            }
        """
        scriptWithRest.register("say") {
            object : Action {
                override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
                    queue.offer(args["text"]?.toString() ?: "")
                    return emptyMap<String, String>()
                }
            }
        }

        scriptWithRest
            .with(args)
            .run()

        assertTrue(queue.poll(5, TimeUnit.SECONDS) == "Bot name: john typicode")
    }

}