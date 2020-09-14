package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import javax.script.Invocable
import javax.script.ScriptEngineManager

class ExpressionAction : Action {

    private val engine = ScriptEngineManager().getEngineByName("javascript")

    override fun execute(context: JsonNode, args: Map<String, Any?>): Boolean {

        val condition = args["condition"]
        engine.eval("var decision = function(context, args) { return ($condition) }")
        val invocable = engine as Invocable
        return invocable.invokeFunction("decision", context, args) as Boolean
    }
}