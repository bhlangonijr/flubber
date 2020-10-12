package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import javax.script.Invocable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager


class JavascriptAction constructor(
    private val script: String,
    private val engine: ScriptEngine =
        ScriptEngineManager().getEngineByName("javascript")
) : Action {

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {

        engine.eval(script)
        val invocable = engine as Invocable
        return invocable.invokeFunction("action", context, args)
    }
}