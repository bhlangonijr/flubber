package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.github.bhlangonijr.flubber.script.Script.Companion.EXIT_NODE_FIELD_NAME

val exitResult = mapOf(Pair(EXIT_NODE_FIELD_NAME, true))

/**
 * Instructs the script engine to terminate the program
 */
class ExitAction : Action {

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        return exitResult
    }
}