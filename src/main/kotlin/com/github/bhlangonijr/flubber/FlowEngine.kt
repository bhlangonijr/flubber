package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.Callback.Companion.THREAD_ID_FIELD
import com.github.bhlangonijr.flubber.Event.Companion.EVENT_NAME_FIELD
import com.github.bhlangonijr.flubber.action.ForEachResult
import com.github.bhlangonijr.flubber.action.MenuResult
import com.github.bhlangonijr.flubber.context.Context
import com.github.bhlangonijr.flubber.context.Context.Companion.ASYNC_FIELD
import com.github.bhlangonijr.flubber.context.Context.Companion.ELEMENTS_FIELD
import com.github.bhlangonijr.flubber.context.Context.Companion.EMPTY_OBJECT
import com.github.bhlangonijr.flubber.context.Context.Companion.GLOBAL_ARGS_FIELD
import com.github.bhlangonijr.flubber.context.Context.Companion.MAIN_THREAD_ID
import com.github.bhlangonijr.flubber.context.Context.Companion.MAX_STACK_SIZE
import com.github.bhlangonijr.flubber.context.Context.Companion.PATH_FIELD
import com.github.bhlangonijr.flubber.context.ExecutionState
import com.github.bhlangonijr.flubber.context.StackFrame
import com.github.bhlangonijr.flubber.script.*
import com.github.bhlangonijr.flubber.script.Script.Companion.ACTION_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.DECISION_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.DO_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.ELSE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.EXIT_NODE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.ITERATION_RESULT_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.PARALLEL_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SEQUENCE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SET_ELEMENT_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SET_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SET_GLOBAL_FIELD_NAME
import com.github.bhlangonijr.flubber.util.Util.Companion.bindVars
import com.github.bhlangonijr.flubber.util.Util.Companion.getId
import com.github.bhlangonijr.flubber.util.Util.Companion.jsonException
import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson
import com.github.bhlangonijr.flubber.util.Util.Companion.nodeToMap
import com.github.bhlangonijr.flubber.util.Util.Companion.objectToNode
import kotlinx.coroutines.Deferred
import mu.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class)
class FlowEngine {

    private val logger = KotlinLogging.logger {}
    private val processMonitorMap = mutableMapOf<String, Context>()
    private val dispatcherExecutor = newSingleThreadContext("dispatcher-thread")

    fun run(context: () -> Context): Context = run(context.invoke())

    fun run(context: Context): Context = runBlocking {

        if (context.threadStateValue(MAIN_THREAD_ID) != ExecutionState.NEW) {
            context.invokeExceptionListeners(ScriptStateException("Script already running"))
        } else {
            logger.debug { "Executing script ${context.script.name}" }
            dispatchToEventLoop(context)
        }
        context
    }

    fun run(context: Context, callback: Callback): Context = runBlocking {

        if (context.threadStateValue(callback.threadId) != ExecutionState.WAITING) {
            context.invokeExceptionListeners(ScriptStateException("Script not in awaiting state"))
        } else {
            logger.debug { "Callback script ${context.script.name}" }
            dispatchToEventLoop(context) {
                context.pop(callback.threadId)?.let { frame ->
                    val result: Any = if (callback.result.isObject) {
                        nodeToMap(callback.result)
                    } else {
                        callback.result.asText()
                    }
                    pushCallbackResponseToStack(
                        context,
                        callback.threadId,
                        frame.sequence,
                        frame.actionIndex,
                        frame.sequence,
                        frame.args,
                        result
                    )
                    context.setThreadState(callback.threadId, ExecutionState.RUNNING)
                    logger.debug { "Callback resuming script ${context.script.name} and response: $result" }
                }
            }
        }
        context
    }

    fun run(context: Context, event: Event): Context = runBlocking {

        if (context.threadStateValue(MAIN_THREAD_ID) == ExecutionState.FINISHED) {
            context.invokeExceptionListeners(ScriptStateException("Script execution is already terminated"))
        } else {
            logger.debug { "Script hook ${event.name}" }
            dispatchToEventLoop(context) {
                context.script.hooks()
                    ?.filter { it.get(EVENT_NAME_FIELD)?.asText()?.equals(event.name) ?: false }
                    ?.let { hooks ->
                        val threadId = getId(event.name ?: "hook")
                        processDoElseBlock(hooks.first(), true, context, event.args, threadId)
                        context.setThreadState(threadId, ExecutionState.RUNNING)
                        logger.debug { "Script hook calling event ${event.name}" }
                    }
            }
        }
        context
    }

    private suspend fun dispatchToEventLoop(
        context: Context,
        runSequentially: suspend () -> Any? = {}
    ) = coroutineScope {

        withContext(dispatcherExecutor) {
            runSequentially.invoke()
            if (processMonitorMap[context.id] == null) {
                processMonitorMap[context.id] = context
                launch {
                    try {
                        logger.info { "Running: ${context.id}" }
                        runMainEventLoop(context)
                    } finally {
                        processMonitorMap.remove(context.id)
                    }
                }
            }
        }
    }

    private suspend fun runMainEventLoop(context: Context) = coroutineScope {

        while (context.running) {
            val runningThreads = mutableListOf<Deferred<Long>>()
            for (threadId in context.state.fieldNames()) {
                val deferred = async {
                    runThreadLoop(context, threadId)
                }
                runningThreads.add(deferred)
            }
            val iterationTime = runningThreads.sumOf { it.await() }
            logger.debug { "Iteration time: $iterationTime" }
        }
    }

    private suspend fun runThreadLoop(context: Context, threadId: String): Long = coroutineScope {

        val init = System.currentTimeMillis()
        var exceptionThrown = false
        while (context.running(threadId)) {
            try {
                executeOneStep(context, threadId)
            } catch (exception: Exception) {
                logger.debug(exception) { "Exception caught executing context" }
                context.invokeExceptionListeners(ScriptException("Script error", exception))
                context.script.exceptionally()
                    ?.let { action ->
                        if (exceptionThrown) {
                            context.setThreadState(threadId, ExecutionState.FINISHED)
                        } else {
                            exceptionThrown = true
                            context.threadStack(threadId).removeAll()
                            processDoElseBlock(
                                action,
                                true,
                                context,
                                jsonException(exception),
                                threadId
                            )
                        }
                    } ?: throw NotHandledScriptException("Not handled Script error", exception)
            }
        }
        System.currentTimeMillis() - init
    }

    private suspend fun executeOneStep(context: Context, threadId: String) = coroutineScope {

        val initialState = context.threadStateValue(threadId)
        logger.trace { "Stack: ${context.stack.toPrettyString()}" +
                ", \n args: ${context.globalArgs}"
        }
        context.next(threadId)?.let { frame ->
            logger.trace { "Next frame: $frame" }
            val actionPath = frame.previousFrame?.path ?: "$threadId-${frame.sequenceId}-"
            if (frame.sequenceType) {
                // initialize sequence stack with first action
                context.push(
                    threadId,
                    StackFrame.create(
                        path = actionPath,
                        sequenceId = frame.sequenceId
                    )
                )
            } else {
                val action = frame.node
                val args = nodeToMap(action[GLOBAL_ARGS_FIELD] ?: EMPTY_OBJECT)
                val globalArgs = context.globalArgs
                bindActionArguments(actionPath, globalArgs, args, threadId)
                if (args[ASYNC_FIELD] == true) {
                    context.setThreadState(threadId, ExecutionState.WAITING)
                }
                val result = executeAction(context, action, args, globalArgs)
                populateActionResult(actionPath, context, args, result)
                context.push(
                    threadId,
                    StackFrame.create(
                        path = actionPath,
                        sequenceId = frame.sequenceId,
                        actionIndex = frame.actionIndex,
                        args = objectToNode(args),
                        result = result
                    )
                )
                processActionResult(actionPath, context, args, action, threadId, result)
            }
        }
        val finalState = context.threadStateValue(threadId)
        if (initialState != finalState) {
            context.invokeStateListeners(threadId, finalState)
        }
    }

    private suspend fun bindActionArguments(
        actionPath: String,
        globalArgs: ObjectNode,
        args: MutableMap<String, Any?>,
        threadId: String
    ) = coroutineScope {
        args[THREAD_ID_FIELD] = threadId
        args[PATH_FIELD] = actionPath
        bindVars("", args, globalArgs)
        bindVars(actionPath, args, globalArgs, true)
    }

    private suspend fun executeAction(
        context: Context,
        action: JsonNode,
        args: MutableMap<String, Any?>,
        globalArgs: ObjectNode
    ): Any? = coroutineScope {

        val actionName = when {
            action.hasNonNull(ACTION_FIELD_NAME) -> action[ACTION_FIELD_NAME].asText()
            action.hasNonNull(DECISION_FIELD_NAME) -> action[DECISION_FIELD_NAME].asText()
            else -> throw NotValidObjectException("Object is neither a valid action nor decision: $action")
        }
        val actionFunction = context.script.actionMap[actionName]
        if (actionFunction == null) {
            throw ActionNotFoundException("Action is not registered: [$actionName]")
        } else {
            val result = actionFunction.execute(globalArgs, args)
            logger.debug { "Called [$actionName] with args [$args] and result: [$result] " }
            result
        }
    }

    private suspend fun populateActionResult(
        actionPath: String,
        context: Context,
        args: MutableMap<String, Any?>,
        result: Any?
    ) = coroutineScope {

        result?.let {
            args[SET_FIELD_NAME]?.let { field ->
                val fullPath = "$actionPath$field"
                context.setVariable(fullPath, objectToNode(result))
            }
            args[SET_GLOBAL_FIELD_NAME]?.let { field ->
                context.setVariable("$field", objectToNode(result))
            }
        }
    }

    private suspend fun processActionResult(
        actionPath: String,
        context: Context,
        args: MutableMap<String, Any?>,
        action: JsonNode,
        threadId: String,
        result: Any?
    ) = coroutineScope {

        when {
            result is Boolean ->
                processDoElseBlock(
                    action,
                    result,
                    context,
                    null,
                    threadId,
                    actionPath
                )
            result is ForEachResult ->
                processDoElseBlock(
                    action,
                    true,
                    context,
                    null,
                    threadId,
                    actionPath,
                    result.elementsNode
                )
            result is MenuResult ->
                processDoElseBlock(
                    result.sequence,
                    result.result,
                    context,
                    null,
                    threadId,
                    actionPath
                )
            result is Map<*, *> && result[EXIT_NODE_FIELD_NAME] == true ->
                context.setThreadState(threadId, ExecutionState.FINISHED)
        }
        context.invokeActionListeners(action, args, result)
    }

    private suspend fun processDoElseBlock(
        action: JsonNode,
        result: Boolean,
        context: Context,
        blockArgValues: JsonNode? = null,
        threadId: String,
        path: String? = null,
        iterateOverMap: JsonNode = makeJson()
    ) = coroutineScope {

        val actionArgs = action.get(GLOBAL_ARGS_FIELD) ?: action
        val block = when {
            context.threadStack(threadId).size() > MAX_STACK_SIZE ->
                throw ScriptStackOverflowException("Script stack overflow")
            result && actionArgs.hasNonNull(DO_FIELD_NAME) -> actionArgs.get(DO_FIELD_NAME)
            !result && actionArgs.hasNonNull(ELSE_FIELD_NAME) -> actionArgs.get(ELSE_FIELD_NAME)
            else -> throw ScriptStateException("Can't find a sequence to execute")
        }
        val sequenceId = block.get(SEQUENCE_FIELD_NAME).asText()
        val currentPath = path ?: "$threadId-"
        val blockArgs = nodeToMap(block.get(GLOBAL_ARGS_FIELD) ?: EMPTY_OBJECT)
        blockArgValues?.let { bindVars("", blockArgs, it) }
        val globalArgs = context.globalArgs
        bindVars("", blockArgs, globalArgs)
        bindVars(currentPath, blockArgs, globalArgs, true)

        val elements = iterateOverMap[ELEMENTS_FIELD] as ArrayNode?

        elements?.let {
            unrollIterationFlow(
                context,
                threadId,
                sequenceId,
                iterateOverMap,
                "$currentPath$sequenceId-",
                blockArgs,
                it
            )
        }
        pushSequenceToStack(
            context,
            threadId,
            "$currentPath$sequenceId-",
            iterateOverMap,
            sequenceId,
            blockArgs
        )
    }

    private suspend fun unrollIterationFlow(
        context: Context,
        threadId: String,
        sequenceId: String,
        args: JsonNode,
        sequencePath: String,
        blockArgs: MutableMap<String, Any?>,
        elements: ArrayNode
    ) = coroutineScope {

        val setField = args[SET_ELEMENT_FIELD_NAME]?.asText()
            ?: ITERATION_RESULT_FIELD_NAME
        val isParallel = args[PARALLEL_FIELD_NAME]?.asBoolean() ?: false
        val element = elements.remove(0)
        context.setVariable("${sequencePath}$setField", objectToNode(element))

        elements.reversed().forEachIndexed { index, childElement ->
            val childThreadId = when {
                isParallel -> getId(threadId)
                else -> threadId
            }
            val childPath = when {
                isParallel -> "$childThreadId-$sequenceId-($index)-"
                else -> "$sequencePath$sequenceId-($index)-"
            }
            context.setVariable("${childPath}$setField", objectToNode(childElement))
            pushSequenceToStack(
                context,
                childThreadId,
                childPath,
                childElement,
                sequenceId,
                blockArgs
            )
            if (isParallel) {
                context.setThreadState(childThreadId, ExecutionState.RUNNING)
            }
        }
    }

    private suspend fun pushSequenceToStack(
        context: Context,
        threadId: String,
        sequencePath: String,
        element: JsonNode,
        sequenceId: String,
        args: MutableMap<String, Any?>
    ) = coroutineScope {
        // populate stack variables with arguments passed to sequence
        objectToNode(args).fields().forEach { field ->
            context.setVariable(
                "$sequencePath${field.key}", field.value
            )
        }
        // push a new sequence entry point to stack
        context.push(
            threadId,
            StackFrame.create(
                path = sequencePath,
                sequenceId = sequenceId,
                sequenceType = true,
                args = element
            )
        )
    }

    private suspend fun pushCallbackResponseToStack(
        context: Context,
        threadId: String,
        sequencePath: String,
        actionIndex: Int,
        sequenceId: String,
        args: JsonNode,
        result: Any
    ) = coroutineScope {

        val objectResult = objectToNode(result)
        args[SET_FIELD_NAME]?.asText()?.let { field ->
            context.setVariable("${sequencePath}$field", objectResult)
        }
        args[SET_GLOBAL_FIELD_NAME]?.asText()?.let { field ->
            context.setVariable(field, objectResult)
        }
        context.push(
            threadId,
            StackFrame.create(
                path = sequencePath,
                sequenceId = sequenceId,
                actionIndex = actionIndex,
                args = args,
                result = result,
                sequenceType = false
            )
        )
    }
}