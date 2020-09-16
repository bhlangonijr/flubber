package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.github.bhlangonijr.flubber.script.Script.Companion.EXIT_NODE_FIELD_NAME

class ExitAction: Action {
    override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
        return mapOf(Pair(EXIT_NODE_FIELD_NAME, true))
    }
}