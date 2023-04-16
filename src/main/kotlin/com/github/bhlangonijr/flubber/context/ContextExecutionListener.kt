package com.github.bhlangonijr.flubber.context

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.coroutineScope

typealias ActionEvent = suspend (node: JsonNode, args: MutableMap<String, Any?>, result: Any?) -> Unit
typealias StateEvent = suspend (threadId: String, state: ExecutionState) -> Unit

open class ContextExecutionListener {

    private val actionListeners: MutableList<ActionEvent> = mutableListOf()
    private val stateListeners: MutableList<StateEvent> = mutableListOf()
    private val onCompleteListeners: MutableList<suspend () -> Unit> = mutableListOf()
    private val exceptionListeners: MutableList<suspend (e: Throwable) -> Unit> = mutableListOf()

    suspend fun onAction(
        action: suspend (
            node: JsonNode,
            args: MutableMap<String, Any?>,
            result: Any?
        ) -> Unit
    ): ContextExecutionListener = coroutineScope {

        actionListeners.add(action)
        return@coroutineScope this@ContextExecutionListener
    }

    suspend fun onException(action: suspend (e: Throwable) -> Unit): ContextExecutionListener = coroutineScope {

        exceptionListeners.add(action)
        return@coroutineScope this@ContextExecutionListener
    }

    suspend fun onStateChange(state: StateEvent): ContextExecutionListener = coroutineScope {

        stateListeners.add(state)
        return@coroutineScope this@ContextExecutionListener
    }

    suspend fun onComplete(action: suspend () -> Unit): ContextExecutionListener = coroutineScope {

        onCompleteListeners.add(action)
        return@coroutineScope this@ContextExecutionListener
    }

    suspend fun invokeActionListeners(node: JsonNode, args: MutableMap<String, Any?>, result: Any?) = coroutineScope {

        actionListeners.forEach { it.invoke(node, args, result) }
    }

    suspend fun invokeStateListeners(threadId: String, state: ExecutionState) = coroutineScope {

        stateListeners.forEach { it.invoke(threadId, state) }
    }

    suspend fun invokeExceptionListeners(e: Throwable) = coroutineScope {

        exceptionListeners.forEach { it.invoke(e) }
    }

    suspend fun invokeOnCompleteListeners() = coroutineScope {

        onCompleteListeners.forEach { it.invoke() }
    }

    suspend fun unregisterActionListeners() = coroutineScope {

        actionListeners.clear()
    }

    suspend fun unregisterStateListeners() = coroutineScope {

        stateListeners.clear()
    }

    suspend fun unregisterExceptionListeners() = coroutineScope {

        exceptionListeners.clear()
    }

    suspend fun unregisterOnCompleteListeners() = coroutineScope {

        onCompleteListeners.clear()
    }

    suspend fun unregisterListeners() = coroutineScope {

        actionListeners.clear()
        stateListeners.clear()
        exceptionListeners.clear()
        onCompleteListeners.clear()
    }
}