package com.github.bhlangonijr.flubber.action

import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Building block of the script for implementing specific script actions
 */

interface Action {

    fun execute(context: ObjectNode, args: Map<String, Any?>): Any?
}