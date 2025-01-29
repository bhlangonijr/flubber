package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.node.ObjectNode
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import javax.script.Invocable
import javax.script.ScriptEngine
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess


class JavascriptAction(
    private val script: String
) : Action {
    override fun execute(context: ObjectNode, args: Map<String, Any?>): Any? {
        val engine: ScriptEngine = GraalJSScriptEngine.create(null,
            Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowNativeAccess(true)
                .allowHostClassLookup { true }
                .allowExperimentalOptions(true)
                .allowCreateThread(true)
                .option("js.nashorn-compat", "true"))
        engine.eval(script)
        val invocable = engine as Invocable
        return invocable.invokeFunction("action", context, args)
    }
}