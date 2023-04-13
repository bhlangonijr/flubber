package com.github.bhlangonijr.flubber.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.bhlangonijr.flubber.context.Context.Companion.EXCEPTION_FIELD
import kotlin.random.Random

class Util {

    companion object {

        private val mapper = ObjectMapper().registerKotlinModule()

        fun loadResource(name: String): String = this::class.java.getResource(name).readText(Charsets.UTF_8)

        fun nodeToMap(node: JsonNode): MutableMap<String, Any?> =
            mapper.convertValue(node, object : TypeReference<MutableMap<String, Any?>>() {})

        fun objectToNode(obj: Any): JsonNode = mapper.valueToTree(obj)

        fun bindVars(
            fullPath: String,
            args: MutableMap<String, Any?>,
            globalArgs: JsonNode,
            replaceBlank: Boolean = false
        ) {

            for (entry in args.entries) {
                if (entry.value is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    bindVars(fullPath, entry.value as MutableMap<String, Any?>, globalArgs)
                } else if (entry.value is String) {
                    val text = entry.value as String
                    if (text.indexOf("}}") > text.indexOf("{{")) {
                        val vars = text.split("}}")
                            .filter { it.contains("{{") }
                            .map { it.split("{{")[1] }
                        for (variable in vars) {
                            val path = "$fullPath$variable".replace(".", "/")
                            val resolved = globalArgs.at("/$path").asText()
                            val newText = args[entry.key] as String
                            if (resolved.isNotEmpty() || replaceBlank) {
                                args[entry.key] = newText.replace("{{$variable}}", resolved)
                            }
                        }
                    }
                }
            }
        }

        fun jsonException(e: Throwable): JsonNode {
            val details = mapper.createObjectNode()
            details.put("message", e.message)
            e.cause?.message?.let { details.put("cause", it) }
            details.put("stacktrace", e.stackTraceToString())
            val node = mapper.createObjectNode()
            node.set<ObjectNode>(EXCEPTION_FIELD, details)
            return node
        }

        fun makeJson(): ObjectNode = mapper.createObjectNode()

        fun makeJsonArray(): ArrayNode = mapper.createArrayNode()

        fun getId(prefix: String): String = "$prefix-${Random.nextInt(1000000, 9999999)}"
    }
}