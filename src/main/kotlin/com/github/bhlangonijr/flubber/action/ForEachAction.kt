package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.bhlangonijr.flubber.context.Context
import com.github.bhlangonijr.flubber.context.Context.Companion.ELEMENTS_FIELD
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.script.Script.Companion.ITERATION_RESULT_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SET_ELEMENT_FIELD_NAME
import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson

/**
 * Run a do-else statement for each element contained in the arguments
 */
class ForEachAction : Action {

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        val iterateOverNode = args[Script.ITERATE_OVER_FIELD_NAME]?.let {
            val actionPath = args[Context.PATH_FIELD]
            val path = "$actionPath$it".replace(".", "/")
            val node = context.at("/$path")
            val newNode = makeJson()
            val setVariable = "${args[SET_ELEMENT_FIELD_NAME] ?: ITERATION_RESULT_FIELD_NAME}"
            newNode.put(SET_ELEMENT_FIELD_NAME, setVariable)
            when (node) {
                is ArrayNode -> newNode.putArray(ELEMENTS_FIELD).addAll(node)
                else -> newNode.putArray(ELEMENTS_FIELD).add(node)
            }
            newNode
        } ?: Context.EMPTY_OBJECT
        return ForEachResult(iterateOverNode)
    }
}

data class ForEachResult(val elementsNode: JsonNode)