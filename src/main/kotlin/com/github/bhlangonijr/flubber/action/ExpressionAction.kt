package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import javax.script.Invocable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Executes de do-else block depending on the result of the expression
 */
class ExpressionAction(
    private val engine: ScriptEngine =
        ScriptEngineManager().getEngineByName("javascript")
) : Action {

    override fun execute(context: JsonNode, args: Map<String, Any?>): Boolean {

        val condition = args["condition"]
        engine.eval("var decision = function(context, args) { return ($condition) }")
        val invocable = engine as Invocable
        return invocable.invokeFunction("decision", context, args) as Boolean
    }
}