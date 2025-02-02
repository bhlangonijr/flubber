package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.util.Util
import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson

/**
 * Execute the sequence for which the option matches `code` or `similar` attributes
 */
class MenuAction : Action {

    override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {

        val received = args["text"].toString().lowercase().trim()
        return args["options"]?.let { optionsArg ->
            val options = Util.objectToNode(optionsArg)
            if (options is ArrayNode) {
                return options.firstOrNull { node ->
                    node.get("code").asText().lowercase().trim() == received ||
                            (node.get("similar") as ArrayNode).any { it.asText().lowercase().trim() == received }
                }?.let { match ->
                    MenuResult(true, match)
                } ?: MenuResult(false, Util.objectToNode(args))
            }
        } ?: MenuResult(false, makeJson())
    }
}

data class MenuResult(
    val result: Boolean,
    val sequence: JsonNode
)