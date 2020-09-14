package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import javax.script.Invocable
import javax.script.ScriptEngineManager


class JavascriptAction constructor(private val script: String) : Action {

    private val engine = ScriptEngineManager().getEngineByName("javascript")

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        engine.eval(script)
        val invocable = engine as Invocable
        return invocable.invokeFunction("action", context, args)
    }
}