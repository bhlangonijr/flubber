package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import javax.script.Invocable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import org.python.core.Options

class PythonAction constructor(
    private val script: String,
    private val engine: ScriptEngine = getPythonEngine()
) : Action {

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {

        engine.eval(script)
        val invocable = engine as Invocable
        return invocable.invokeFunction("action", context, args)
    }
}

fun getPythonEngine(): ScriptEngine {
    Options.importSite = false
    return ScriptEngineManager().getEngineByName("python")
}