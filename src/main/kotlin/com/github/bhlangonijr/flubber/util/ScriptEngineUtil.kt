package com.github.bhlangonijr.flubber.util

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import javax.script.ScriptEngine
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess

object ScriptEngineUtil {

    private fun getOption(name: String, defaultValue: String): String {
        return System.getProperty(name, System.getenv(name) ?: defaultValue)
    }

    private val threadLocalEngine = ThreadLocal.withInitial {
        GraalJSScriptEngine.create(
            null,
            Context.newBuilder("js")
                .allowHostAccess(
                    if (getOption("script.allowHostAccess", "true").toBoolean()) HostAccess.ALL else HostAccess.NONE
                )
                .allowNativeAccess(getOption("script.allowNativeAccess", "true").toBoolean())
                .allowHostClassLookup { getOption("script.allowHostClassLookup", "true").toBoolean() }
                .allowExperimentalOptions(getOption("script.allowExperimentalOptions", "true").toBoolean())
                .allowCreateThread(getOption("script.allowCreateThread", "true").toBoolean())
                .option("js.nashorn-compat", getOption("script.js.nashorn-compat", "true"))
        )
    }

    fun getThreadLocalInstance(): ScriptEngine = threadLocalEngine.get()
}