package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import javax.script.Invocable
import javax.script.ScriptEngine
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess


/**
 * Executes de do-else block depending on the result of the expression
 * Alternatively evaluates an arbitrary javascript expression
 */
class ExpressionAction: Action {

    override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {
        val engine: ScriptEngine = GraalJSScriptEngine.create(null,
            Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowNativeAccess(true)
                .allowHostClassLookup { true }
                .allowExperimentalOptions(true)
                .allowCreateThread(true)
                .option("js.nashorn-compat", "true"))
        val expression = args["condition"] ?: args["text"]
        engine.eval("var expression = function(context, args) { return ($expression) }")
        val invocable = engine as Invocable
        return invocable.invokeFunction("expression", context, args)
    }
}