package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.util.ScriptEngineUtil
import javax.script.Invocable
import javax.script.ScriptEngine

class JavascriptAction(
    private val script: String
) : Action {
    override fun execute(context: ObjectNode, args: Map<String, Any?>): Any? {
        val engine: ScriptEngine = ScriptEngineUtil.getThreadLocalInstance()
        engine.eval(script)
        val invocable = engine as Invocable
        return invocable.invokeFunction("action", context, args)
    }
}