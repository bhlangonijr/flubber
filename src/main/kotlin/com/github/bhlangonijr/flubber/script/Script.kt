package com.github.bhlangonijr.flubber.script

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.bhlangonijr.flubber.context.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.stream.Collectors

class Script private constructor(val root: JsonNode) {

    companion object {

        const val FLOW_FIELD_NAME = "flow"
        const val SEQUENCE_FIELD_NAME = "sequence"
        const val HOOKS_FIELD_NAME = "hooks"
        const val EXCEPTIONALLY_FIELD_NAME = "exceptionally"
        const val ID_FIELD_NAME = "@id"
        const val ACTION_FIELD_NAME = "action"
        const val DECISION_FIELD_NAME = "decision"
        const val MAIN_FLOW_ID = "main"
        const val DO_FIELD_NAME = "do"
        const val ELSE_FIELD_NAME = "else"

        private val mapper = ObjectMapper().registerKotlinModule()

        fun from(source: URL): Script {

            val data = source.readText()
            return from(data)
        }

        fun from(source: InputStream): Script {

            val data: String = BufferedReader(InputStreamReader(source))
                .lines().collect(Collectors.joining("\n"))
            return from(data)
        }

        fun from(script: String): Script {

            val scriptJson = mapper.readTree(script)
            return from(scriptJson)
        }

        fun from(script: JsonNode): Script {

            return Script(script)
        }
    }

    val name: String
        get() = root[ID_FIELD_NAME].asText("no-name")

    fun main(): ArrayNode? = sequence(MAIN_FLOW_ID)

    fun flow(): ArrayNode? = (root[FLOW_FIELD_NAME] as ArrayNode?)

    fun sequence(id: String): ArrayNode? =
        flow()
            ?.first { it[ID_FIELD_NAME].asText() == id }
            ?.get(SEQUENCE_FIELD_NAME) as ArrayNode?

    fun action(sequenceId: String, actionId: String): JsonNode? =
        sequence(sequenceId)
            ?.first { it[ID_FIELD_NAME].asText() == actionId }

    fun action(sequenceId: String, actionIndex: Int): JsonNode? =
        sequence(sequenceId)?.get(actionIndex)

    fun hooks(): ArrayNode? = (root[HOOKS_FIELD_NAME] as ArrayNode?)

    fun exceptionally(): JsonNode? = root[EXCEPTIONALLY_FIELD_NAME]

    fun with(args: String): Context = Context.create(this, args)

}