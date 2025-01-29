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

        fun loadResource(name: String): String {
            return this::class.java.getResource(name)?.readText(Charsets.UTF_8)
                ?: throw IllegalArgumentException("Resource not found: $name")
        }

        fun nodeToMap(node: JsonNode): MutableMap<String, Any?> =
            mapper.convertValue(node, object : TypeReference<MutableMap<String, Any?>>() {})

        fun objectToNode(obj: Any): JsonNode = mapper.valueToTree(obj)

        fun bindVars(
            fullPath: String,
            args: MutableMap<String, Any?>,
            globalArgs: JsonNode,
            replaceBlank: Boolean = false
        ) {
            args.entries.forEach { (key, value) ->
                when (value) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        bindVars(fullPath, value as MutableMap<String, Any?>, globalArgs, replaceBlank)
                    }
                    is List<*> -> {
                        args[key] = processList(value, fullPath, globalArgs, replaceBlank)
                    }
                    is String -> {
                        args[key] = bindVarInString(value, replaceBlank, globalArgs, fullPath)
                    }
                }
            }
        }

        private fun processList(
            list: List<*>,
            fullPath: String,
            globalArgs: JsonNode,
            replaceBlank: Boolean
        ): Any {
            return if (list.isNotEmpty() && list.all { it is String }) {
                list.map { bindVarInString(it as String, replaceBlank, globalArgs, fullPath) }
            } else {
                list.forEach {
                    if (it is MutableMap<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        bindVars(fullPath, it as MutableMap<String, Any?>, globalArgs, replaceBlank)
                    }
                }
                list
            }
        }

        private fun bindVarInString(
            text: String,
            replaceBlank: Boolean,
            globalArgs: JsonNode,
            fullPath: String
        ): String {
            var newText = text
            val regex = Regex("""\{\{(.*?)\}\}""")
            regex.findAll(text).forEach { matchResult ->
                val variable = matchResult.groupValues[1]
                val path = "$fullPath$variable".replace(".", "/")
                val resolved = globalArgs.at("/$path").asText()
                if (resolved.isNotEmpty() || replaceBlank) {
                    newText = newText.replace("{{$variable}}", resolved)
                }
            }
            return newText
        }

        fun jsonException(e: Throwable): JsonNode {
            val details = mapper.createObjectNode().apply {
                put("message", e.message)
                e.cause?.message?.let { put("cause", it) }
                put("stacktrace", e.stackTraceToString())
            }
            return mapper.createObjectNode().set<ObjectNode>(EXCEPTION_FIELD, details)
        }

        fun makeJson(): ObjectNode = mapper.createObjectNode()

        fun makeJsonArray(): ArrayNode = mapper.createArrayNode()

        fun getId(prefix: String): String = "$prefix-${Random.nextInt(1000000, 9999999)}"
    }
}