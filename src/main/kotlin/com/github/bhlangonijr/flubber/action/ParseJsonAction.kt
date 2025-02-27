package com.github.bhlangonijr.flubber.action

import com.bazaarvoice.jolt.Chainr
import com.bazaarvoice.jolt.JsonUtils
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.bhlangonijr.flubber.util.Util.Companion.nodeToMap

/**
 * Parse a JSON string into JSON node tree object
 */
class ParseJsonAction : Action {

    companion object {
        private val objectMapper = ObjectMapper().registerKotlinModule()
    }

    override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {

        val json = args["text"]?.toString() ?: "{}"
        val result = args["spec"]?.toString()?.let { spec ->
            val specJson: List<*> = JsonUtils.jsonToList(spec)
            val chainr: Chainr = Chainr.fromSpec(specJson)
            val inputJson: Any = JsonUtils.jsonToObject(json)
            val outputJson: Any = chainr.transform(inputJson)
            JsonUtils.toJsonString(outputJson)
        } ?: json

        return nodeToMap(objectMapper.readTree(result))
    }
}