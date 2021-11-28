package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.bhlangonijr.flubber.util.Util.Companion.nodeToMap

/**
 * Parse a JSON string into JSON node tree object
 */
class ParseJsonAction : Action {

    companion object {
        private val objectMapper = ObjectMapper().registerModule(KotlinModule())
    }

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        return nodeToMap(objectMapper.readTree(args["text"]?.toString() ?: "{}"))
    }
}