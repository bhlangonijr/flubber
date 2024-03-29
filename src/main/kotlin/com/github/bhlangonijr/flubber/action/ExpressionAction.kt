package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import javax.script.Invocable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Executes de do-else block depending on the result of the expression
 * Alternatively evaluates an arbitrary javascript expression
 */
class ExpressionAction(
    private val engine: ScriptEngine =
        ScriptEngineManager().getEngineByName("javascript")
) : Action {

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        val expression = args["condition"] ?: args["text"]
        engine.eval("var expression = function(context, args) { return ($expression) }")
        val invocable = engine as Invocable
        return invocable.invokeFunction("expression", context, args)
    }
}