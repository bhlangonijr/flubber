package com.github.bhlangonijr.flubber.context

import com.fasterxml.jackson.databind.JsonNode

typealias ActionEvent = (node: JsonNode, args: MutableMap<String, Any?>, result: Any?) -> Unit
typealias StateEvent = (threadId: String, state: ExecutionState) -> Unit

open class ContextExecutionListener {

    private val actionListeners: MutableList<ActionEvent> = mutableListOf()
    private val stateListeners: MutableList<StateEvent> = mutableListOf()
    private val onCompleteListeners: MutableList<() -> Unit> = mutableListOf()
    private val exceptionListeners: MutableList<(e: Throwable) -> Unit> = mutableListOf()

    fun onAction(
        action: (
            node: JsonNode,
            args: MutableMap<String, Any?>,
            result: Any?
        ) -> Unit
    ): ContextExecutionListener {

        actionListeners.add(action)
        return this
    }

    fun onException(action: (e: Throwable) -> Unit): ContextExecutionListener {

        exceptionListeners.add(action)
        return this
    }

    fun onStateChange(state: StateEvent): ContextExecutionListener {

        stateListeners.add(state)
        return this
    }

    fun onComplete(action: () -> Unit): ContextExecutionListener {

        onCompleteListeners.add(action)
        return this
    }

    fun invokeActionListeners(node: JsonNode, args: MutableMap<String, Any?>, result: Any?) {

        actionListeners.forEach { it.invoke(node, args, result) }
    }

    fun invokeStateListeners(threadId: String, state: ExecutionState) {

        stateListeners.forEach { it.invoke(threadId, state) }
    }

    fun invokeExceptionListeners(e: Throwable) {

        exceptionListeners.forEach { it.invoke(e) }
    }

    fun invokeOnCompleteListeners() {

        onCompleteListeners.forEach { it.invoke() }
    }

    fun unregisterActionListeners() {

        actionListeners.clear()
    }

    fun unregisterStateListeners() {

        stateListeners.clear()
    }

    fun unregisterExceptionListeners() {

        exceptionListeners.clear()
    }

    fun unregisterOnCompleteListeners() {

        onCompleteListeners.clear()
    }

    fun unregisterListeners() {

        actionListeners.clear()
        stateListeners.clear()
        exceptionListeners.clear()
        onCompleteListeners.clear()
    }
}