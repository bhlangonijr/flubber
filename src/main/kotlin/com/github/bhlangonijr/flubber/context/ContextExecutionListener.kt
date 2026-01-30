package com.github.bhlangonijr.flubber.context

import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging

typealias ActionEvent = suspend (node: JsonNode, args: MutableMap<String, Any?>, result: Any?) -> Unit
typealias BeforeActionEvent = suspend (node: JsonNode, args: MutableMap<String, Any?>) -> Unit
typealias StateEvent = suspend (threadId: String, state: ExecutionState) -> Unit

open class ContextExecutionListener {

    private val logger = KotlinLogging.logger {}
    private val actionListeners: MutableList<ActionEvent> = CopyOnWriteArrayList()
    private val beforeActionListeners: MutableList<BeforeActionEvent> = CopyOnWriteArrayList()
    private val stateListeners: MutableList<StateEvent> = CopyOnWriteArrayList()
    private val onCompleteListeners: MutableList<suspend () -> Unit> = CopyOnWriteArrayList()
    private val exceptionListeners: MutableList<suspend (e: Throwable) -> Unit> = CopyOnWriteArrayList()

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

    suspend fun onBeforeAction(
        action: suspend (
            node: JsonNode,
            args: MutableMap<String, Any?>
        ) -> Unit
    ): ContextExecutionListener = coroutineScope {

        beforeActionListeners.add(action)
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

        actionListeners.forEach {
            try { it.invoke(node, args, result) } catch (e: Exception) {
                logger.warn(e) { "Action listener threw exception" }
            }
        }
    }

    suspend fun invokeBeforeActionListeners(node: JsonNode, args: MutableMap<String, Any?>) = coroutineScope {

        beforeActionListeners.forEach {
            try { it.invoke(node, args) } catch (e: Exception) {
                logger.warn(e) { "Before-action listener threw exception" }
            }
        }
    }

    suspend fun invokeStateListeners(threadId: String, state: ExecutionState) = coroutineScope {

        stateListeners.forEach {
            try { it.invoke(threadId, state) } catch (e: Exception) {
                logger.warn(e) { "State listener threw exception" }
            }
        }
    }

    suspend fun invokeExceptionListeners(e: Throwable) = coroutineScope {

        exceptionListeners.forEach {
            try { it.invoke(e) } catch (listenerException: Exception) {
                logger.warn(listenerException) { "Exception listener threw exception" }
            }
        }
    }

    suspend fun invokeOnCompleteListeners() = coroutineScope {

        onCompleteListeners.forEach {
            try { it.invoke() } catch (e: Exception) {
                logger.warn(e) { "OnComplete listener threw exception" }
            }
        }
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