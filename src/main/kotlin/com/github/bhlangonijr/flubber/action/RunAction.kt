package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Run action unconditionally runs the do-else block contained in the node
 */
class RunAction : Action {

    override fun execute(context: ObjectNode, args: Map<String, Any?>): Any {

        return true
    }
}