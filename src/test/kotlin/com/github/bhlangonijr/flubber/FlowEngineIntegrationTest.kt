package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.action.Action
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.util.Util.Companion.loadResource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class FlowEngineIntegrationTest {

    private val scriptWithImports = Script.from(loadResource("/script-example-import.json"))

    val args = """
            {
              "session":{
              "user":"john"
              }
            }
        """

    @Test
    fun `test imported actions`() {

        val engine = FlowEngine()
        val queue = ArrayBlockingQueue<Boolean>(2)

        scriptWithImports.register("waitOnDigits") {
            object : Action {
                override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                    val input = "1000"
                    args["set"]?.let { (context as ObjectNode).put(it as String, input) }
                    args["set"]?.let { (context as ObjectNode).put("COMPLETED", true) }
                    return input
                }
            }
        }

        val ctx = scriptWithImports.with(args)
        engine.run { ctx }.onComplete { queue.offer(ctx.globalArgs.get("COMPLETED").asBoolean()) }
        assertTrue(queue.poll(5, TimeUnit.SECONDS) == true)
    }

}