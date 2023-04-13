package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.bhlangonijr.flubber.context.Context
import com.github.bhlangonijr.flubber.context.Context.Companion.ELEMENTS_FIELD
import com.github.bhlangonijr.flubber.context.Context.Companion.PATH_FIELD
import com.github.bhlangonijr.flubber.script.Script.Companion.ITERATE_OVER_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.PARALLEL_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SET_FOREACH_ELEMENT_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SET_FIELD_NAME
import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson

/**
 * Run a do-else statement for each element contained in the arguments
 */
class ForEachAction : Action {

    companion object {
        const val DEFAULT_SET_FOREACH_ELEMENT_FIELD_NAME = "iterationElement"
    }

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        val iterateOverNode = args[ITERATE_OVER_FIELD_NAME]?.let {
            val actionPath = args[PATH_FIELD]
            val path = "$actionPath$it".replace(".", "/")
            val isParallel = args[PARALLEL_FIELD_NAME] == true
            val node = context.at("/$path")
            val newNode = makeJson()
            val setVariable = "${args[SET_FOREACH_ELEMENT_FIELD_NAME] 
                ?: DEFAULT_SET_FOREACH_ELEMENT_FIELD_NAME}"
            val setResult = "${args[SET_FIELD_NAME]}"
            newNode.put(SET_FOREACH_ELEMENT_FIELD_NAME, setVariable)
            newNode.put(PARALLEL_FIELD_NAME, isParallel)
            newNode.put(SET_FIELD_NAME, setResult)
            actionPath?.let { currentPath -> newNode.put(PATH_FIELD, "$currentPath") }

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