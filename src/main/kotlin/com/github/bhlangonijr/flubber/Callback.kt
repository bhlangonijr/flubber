package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import java.net.URL

class Callback constructor(val data: JsonNode) {

    companion object {

        const val ID_FIELD = "id"
        const val THREAD_ID_FIELD = "threadId"
        const val RESULT_FIELD = "result"

        private val mapper = ObjectMapper().registerKotlinModule()

        fun load(source: URL): Callback = load(mapper.readTree(source))

        fun load(source: InputStream): Callback = load(mapper.readTree(source))

        fun load(data: JsonNode): Callback = Callback(data)

        fun from(source: String): Callback = load(mapper.readTree(source))

    }

    val name: String
        get() = data.get(ID_FIELD).asText()

    val threadId: String
        get() = data.get(THREAD_ID_FIELD).asText()

    val result: JsonNode
        get() = data.get(RESULT_FIELD)
}