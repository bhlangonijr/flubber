package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.Callback.Companion.THREAD_ID_FIELD
import com.github.bhlangonijr.flubber.Event.Companion.EVENT_NAME_FIELD
import com.github.bhlangonijr.flubber.context.Context
import com.github.bhlangonijr.flubber.context.Context.Companion.ASYNC_FIELD
import com.github.bhlangonijr.flubber.context.Context.Companion.EMPTY_OBJECT
import com.github.bhlangonijr.flubber.context.Context.Companion.GLOBAL_ARGS_FIELD
import com.github.bhlangonijr.flubber.context.Context.Companion.MAIN_THREAD_ID
import com.github.bhlangonijr.flubber.context.Context.Companion.MAX_STACK_SIZE
import com.github.bhlangonijr.flubber.context.ExecutionState
import com.github.bhlangonijr.flubber.context.StackFrame
import com.github.bhlangonijr.flubber.script.*
import com.github.bhlangonijr.flubber.script.Script.Companion.ACTION_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.DECISION_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.DO_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.ELSE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.EXIT_NODE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.ITERATE_OVER_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SEQUENCE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SET_FIELD_NAME
import com.github.bhlangonijr.flubber.util.NamedThreadFactory
import com.github.bhlangonijr.flubber.util.Util.Companion.bindVars
import com.github.bhlangonijr.flubber.util.Util.Companion.getId
import com.github.bhlangonijr.flubber.util.Util.Companion.jsonException
import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson
import com.github.bhlangonijr.flubber.util.Util.Companion.nodeToMap
import com.github.bhlangonijr.flubber.util.Util.Companion.objectToNode
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class FlowEngine(
    private val executor: Executor = Executors.newCachedThreadPool(
        NamedThreadFactory("executor-thread")
    )
) {

    private val logger = KotlinLogging.logger {}
    private val processMonitorMap = mutableMapOf<String, Context>()
    private val dispatcherExecutor: Executor = Executors.newSingleThreadExecutor(
        NamedThreadFactory("dispatcher-thread")
    )

    fun run(context: () -> Context): Context = run(context.invoke())

    fun run(context: Context): Context {

        if (context.threadStateValue(MAIN_THREAD_ID) != ExecutionState.NEW) {
            context.invokeExceptionListeners(ScriptStateException("Script already running"))
        } else {
            logger.debug { "Executing script ${context.script.name}" }
            dispatch(context)
        }
        return context

    }

    fun callback(context: Context, callback: Callback): Context {

        if (context.threadStateValue(callback.threadId) != ExecutionState.WAITING) {
            context.invokeExceptionListeners(ScriptStateException("Script not in awaiting state"))
        } else {
            logger.debug { "Callback script ${context.script.name}" }
            dispatch(context) {
                context.pop(callback.threadId)?.let { frame ->
                    val result: Any = if (callback.result.isObject) {
                        nodeToMap(callback.result)
                    } else callback.result.asText()
                    context.push(
                        callback.threadId,
                        StackFrame.create(
                            sequenceId = frame.sequence,
                            actionIndex = frame.actionIndex,
                            args = frame.args,
                            result = result,
                            sequenceType = false
                        )
                    )
                    frame.args[SET_FIELD_NAME]?.let { field ->
                        context.globalArgs.set<JsonNode>(field as String, objectToNode(result))
                    }
                    context.setThreadState(callback.threadId, ExecutionState.RUNNING)
                    logger.debug { "Callback resuming script ${context.script.name} and response: $result" }
                }
            }

        }
        return context
    }

    fun hook(context: Context, event: Event): Context {

        if (context.threadStateValue(MAIN_THREAD_ID) == ExecutionState.FINISHED) {
            context.invokeExceptionListeners(ScriptStateException("Script execution is already terminated"))
        } else {
            logger.debug { "Script hook ${event.name}" }
            dispatch(context) {
                context.script.hooks()
                    ?.filter { it.get(EVENT_NAME_FIELD)?.asText()?.equals(event.name) ?: false }
                    ?.let { hooks ->
                        val threadId = getId(event.name ?: "hook")
                        executeDoElse(hooks.first(), true, context, event.args, Collections.emptyMap(), threadId)
                        context.setThreadState(threadId, ExecutionState.RUNNING)
                        logger.debug { "Script hook calling event ${event.name}" }
                    }
            }
        }
        return context
    }

    private fun dispatch(context: Context, runSequentially: () -> Any? = {}) {

        dispatcherExecutor.execute {
            runSequentially.invoke()
            if (processMonitorMap[context.id] == null) {
                processMonitorMap[context.id] = context
                executor.execute {
                    try {
                        logger.info { "Running: ${context.id}" }
                        execute(context)
                    } finally {
                        processMonitorMap.remove(context.id)
                    }
                }
            }
        }
    }

    private fun execute(context: Context) {

        var onExceptionBlock = false
        while (context.running) {
            for (threadId in context.state.fieldNames()) {
                runCatching {
                    val initialState = context.threadStateValue(threadId)
                    executeOneStep(context, threadId)
                    val finalState = context.threadStateValue(threadId)
                    if (initialState != finalState) {
                        context.invokeStateListeners(threadId, finalState)
                    }
                }.onFailure { exception ->
                    context.invokeExceptionListeners(ScriptException("Script error", exception))
                    context.script.exceptionally()
                        ?.let { action ->
                            if (onExceptionBlock.not()) {
                                onExceptionBlock = true
                                context.threadStack(threadId).removeAll()
                                executeDoElse(
                                    action,
                                    true,
                                    context,
                                    jsonException(exception),
                                    Collections.emptyMap(),
                                    threadId
                                )
                            } else {
                                context.setThreadState(threadId, ExecutionState.FINISHED)
                            }
                        } ?: throw NotHandledScriptException("Not handled Script error", exception)
                }
            }
        }
    }

    private fun executeOneStep(context: Context, threadId: String) {

        if (context.running(threadId)) {
            logger.trace { "stack: ${context.stack.toPrettyString()}" }
            context.next(threadId)?.let { frame ->
                logger.trace { "frame: $frame" }
                //val sequenceArgs = makeJson()
                if (frame.sequenceType) {
                    frame.previousFrame?.let { lastFrame ->
                        val args = objectToNode(lastFrame.args) as ObjectNode
                        args["_elements"]?.takeIf { !it.isEmpty }?.let {
                            val elements = it as ArrayNode
                            val element = elements.remove(0)
                            context.globalArgs.set<JsonNode>("iterationResult", objectToNode(element))
                            if (!elements.isEmpty) {
                                context.push(
                                    threadId, StackFrame.create(
                                        sequenceId = frame.sequenceId,
                                        sequenceType = true,
                                        args = nodeToMap(args)
                                    )
                                )
                            }
                        }
                    }
                    context.push(
                        threadId, StackFrame.create(
                            sequenceId = frame.sequenceId
                        )
                    )
                } else {
                    val action = frame.node
                    val args = nodeToMap(action[GLOBAL_ARGS_FIELD] ?: EMPTY_OBJECT)
                    args[THREAD_ID_FIELD] = threadId
                    val globalArgs = context.globalArgs
                    bindVars(args, globalArgs)
                    if (args[ASYNC_FIELD] == true) {
                        context.setThreadState(threadId, ExecutionState.WAITING)
                    }
                    val result = executeAction(context, action, args, globalArgs)
                    args[SET_FIELD_NAME]?.let { field ->
                        result?.let {
                            globalArgs.set<JsonNode>(field as String, objectToNode(result))
                        }
                    }
                    val iterateOverNode = nodeToMap(args[ITERATE_OVER_FIELD_NAME]?.let {
                        val path = it.toString().replace(".", "/")
                        val node = globalArgs.at("/$path")
                        val newNode = makeJson()
                        when (node) {
                            is ArrayNode -> newNode.putArray("_elements").addAll(node)
                            else -> newNode.putArray("_elements").add(node)
                        }
                        newNode
                    } ?: EMPTY_OBJECT)
                    context.push(
                        threadId, StackFrame.create(
                            sequenceId = frame.sequenceId,
                            actionIndex = frame.actionIndex,
                            args = args,
                            result = result
                        )
                    )
                    when {
                        result is Boolean ->
                            executeDoElse(action, result, context, null, iterateOverNode, threadId)
                        result is Map<*, *> && result[EXIT_NODE_FIELD_NAME] == true ->
                            context.setThreadState(threadId, ExecutionState.FINISHED)
                    }
                    context.invokeActionListeners(action, args, result)
                }
            }
        }
    }

    private fun executeAction(
        context: Context,
        action: JsonNode,
        args: MutableMap<String, Any?>,
        globalArgs: ObjectNode
    ): Any? {

        val actionName = when {
            action.hasNonNull(ACTION_FIELD_NAME) -> action[ACTION_FIELD_NAME].asText()
            action.hasNonNull(DECISION_FIELD_NAME) -> action[DECISION_FIELD_NAME].asText()
            else -> throw NotValidObjectException("Object is neither a valid action nor decision: $action")
        }
        val actionFunction = context.script.actionMap[actionName]
        return if (actionFunction == null) {
            throw ActionNotFoundException("Action is not registered: [$actionName]")
        } else {
            val result = actionFunction.execute(globalArgs, args)
            logger.debug { "Called [$actionName] with args [$args] and result: [$result] " }
            result
        }
    }

    private fun executeDoElse(
        action: JsonNode,
        result: Boolean,
        context: Context,
        blockArgs: JsonNode? = null,
        iterateOverMap: MutableMap<String, Any?> = Collections.emptyMap(),
        threadId: String
    ) {

        when {
            context.threadStack(threadId).size() > MAX_STACK_SIZE ->
                throw ScriptStackOverflowException("Script stack overflow")
            result && action.hasNonNull(DO_FIELD_NAME) -> action.get(DO_FIELD_NAME)
            !result && action.hasNonNull(ELSE_FIELD_NAME) -> action.get(ELSE_FIELD_NAME)
            else -> null
        }?.let { block ->
            val sequence = block.get(SEQUENCE_FIELD_NAME)?.asText()
            val args = nodeToMap(block.get(GLOBAL_ARGS_FIELD) ?: EMPTY_OBJECT)
            blockArgs?.let { bindVars(args, it) }
            val globalArgs = context.globalArgs
            bindVars(args, globalArgs)
            globalArgs.setAll<ObjectNode>(objectToNode(args) as ObjectNode)
            sequence?.let {
                context.push(
                    threadId, StackFrame.create(
                        sequenceId = it,
                        sequenceType = true,
                        args = iterateOverMap
                    )
                )
            }
        }
    }
}