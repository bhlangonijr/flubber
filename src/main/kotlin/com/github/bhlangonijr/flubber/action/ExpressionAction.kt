package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.util.ScriptEngineUtil
import javax.script.Invocable
import javax.script.ScriptEngine

/**
 * Executes de do-else block depending on the result of the expression
 * Alternatively evaluates an arbitrary javascript expression
 */
class ExpressionAction: Action {

    override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
        val engine: ScriptEngine = ScriptEngineUtil.getThreadLocalInstance()
        val expression = args["condition"] ?: args["text"]
        engine.eval("var expression = function(context, args) { return ($expression) }")
        val invocable = engine as Invocable
        return invocable.invokeFunction("expression", context, args)
    }
}