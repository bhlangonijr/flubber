package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.JsonNode

/**
 * Run action unconditionally runs the do-else block contained in the node
 */
class runAction : Action {

    override fun execute(context: JsonNode, args: Map<String, Any?>): Any {

        return true
    }
}