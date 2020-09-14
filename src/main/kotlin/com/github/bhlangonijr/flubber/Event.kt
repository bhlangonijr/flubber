package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import java.net.URL

class Event constructor(val data: JsonNode) {

    companion object {

        const val EVENT_NAME_FIELD = "event"

        private val mapper = ObjectMapper().registerKotlinModule()

        fun load(source: URL): Event = load(mapper.readTree(source))

        fun load(source: InputStream): Event = load(mapper.readTree(source))

        fun load(source: String): Event = load(mapper.readTree(source))

        fun load(data: JsonNode): Event = Event(data)

    }

    val name: String?
        get() = data.get(EVENT_NAME_FIELD)?.asText()
}