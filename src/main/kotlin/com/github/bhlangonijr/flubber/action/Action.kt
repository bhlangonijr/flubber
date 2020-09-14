package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode

/**
 * Building block of the script for implementing specific script actions
 */

interface Action {

    fun execute(context: JsonNode, args: Map<String, Any?>): Any?
}