package com.github.bhlangonijr.flubber

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.bhlangonijr.flubber.Event.Companion.EVENT_NAME_FIELD
import com.github.bhlangonijr.flubber.action.Action
import com.github.bhlangonijr.flubber.action.Actions
import com.github.bhlangonijr.flubber.action.ExitAction
import com.github.bhlangonijr.flubber.action.ExpressionAction
import com.github.bhlangonijr.flubber.context.Context
import com.github.bhlangonijr.flubber.context.Context.Companion.ARGS_FIELD
import com.github.bhlangonijr.flubber.context.Context.Companion.EMPTY_OBJECT
import com.github.bhlangonijr.flubber.context.Context.Companion.MAX_STACK_SIZE
import com.github.bhlangonijr.flubber.context.ExecutionState
import com.github.bhlangonijr.flubber.context.StackFrame
import com.github.bhlangonijr.flubber.script.*
import com.github.bhlangonijr.flubber.script.Script.Companion.ACTION_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.DECISION_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.DO_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.ELSE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.RELOAD_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.SEQUENCE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.EXIT_NODE_FIELD_NAME
import com.github.bhlangonijr.flubber.script.Script.Companion.URL_FIELD_NAME
import com.github.bhlangonijr.flubber.util.Util.Companion.objectToNode
import com.github.bhlangonijr.flubber.util.Util.Companion.bindVars
import com.github.bhlangonijr.flubber.util.Util.Companion.jsonException
import com.github.bhlangonijr.flubber.util.Util.Companion.nodeToMap
import mu.KotlinLogging

class FlowEngine {

    private val logger = KotlinLogging.logger {}
    private val actionMap = mutableMapOf<String, Action>()

    init {
        register("expression", ExpressionAction())
        register("exit", ExitAction())
    }

    fun run(context: () -> Context): Context = run(context.invoke())

    fun run(context: Context): Context {

        logger.debug { "Executing script ${context.script.name}" }
        loadImports(context)
        execute(context)
        return context
    }

    fun hook(context: Context, event: Event): Context {

        logger.debug { "Callback script ${event.name}" }
        context.script.hooks()
            ?.filter { it.get(EVENT_NAME_FIELD)?.asText()?.equals(event.name) ?: false }
            ?.let { hooks ->
                executeDoElse(hooks.first(), true, context, event.data)
            }
        execute(context)
        return context
    }

    fun register(name: String, action: Action) {

        actionMap[name] = action
        logger.info { "Registered action: $name" }
    }

    fun register(name: String, action: () -> Action) = register(name, action.invoke())


    private fun execute(context: Context) {

        var countException = 0
        while (context.running) {
            runCatching {
                context.next()?.let { frame ->
                    val action = frame.node
                    val args = nodeToMap(action["args"] ?: EMPTY_OBJECT)
                    val globalArgs = context.globalArgs
                    bindVars(args, globalArgs)
                    val result = executeAction(action, args, globalArgs)
                    context.push(StackFrame.create(frame.sequenceId, frame.actionIndex, args, result))
                    processResult(action, result, context)
                    logger.trace { "stack: ${context.stack.toPrettyString()}" }
                }
            }.onFailure { exception ->
                context.script.exceptionally()
                    ?.let { action ->
                        if (countException++ < 1) {
                            context.stack.removeAll()
                            executeDoElse(action, true, context, jsonException(exception))
                        } else {
                            context.state = ExecutionState.FINISHED
                            throw ScriptException("Script error", exception)
                        }
                    } ?: throw NotHandledScriptException("Not handled Script error", exception)
            }
        }

    }

    private fun executeAction(action: JsonNode, args: MutableMap<String, Any?>, globalArgs: ObjectNode): Any? {

        val actionName = when {
            action.hasNonNull(ACTION_FIELD_NAME) -> action[ACTION_FIELD_NAME].asText()
            action.hasNonNull(DECISION_FIELD_NAME) -> action[DECISION_FIELD_NAME].asText()
            else -> throw NotValidObjectException("Object is neither a valid action nor decision: $action")
        }
        val actionFunction = actionMap[actionName]
        return if (actionFunction == null) {
            throw ActionNotFoundException("Action is not registered: [$actionName]")
        } else {
            val result = actionFunction.execute(globalArgs, args)
            logger.debug { "Called [$actionName] with args [$args] and result: [$result] " }
            result
        }
    }

    private fun processResult(action: JsonNode, result: Any?, context: Context) {

        if (result is Boolean) {
            executeDoElse(action, result, context)
        } else if (result is Map<*, *>) {
            val resultNode = objectToNode(result)
            if (resultNode.get(EXIT_NODE_FIELD_NAME)?.asBoolean() == true) {
                context.state = ExecutionState.FINISHED
            }
        }

    }

    private fun executeDoElse(action: JsonNode, result: Boolean, context: Context, blockArgs: JsonNode? = null) {

        when {
            context.stack.size() > MAX_STACK_SIZE -> throw ScriptStackOverflowException("Script stack overflow")
            result && action.hasNonNull(DO_FIELD_NAME) -> action.get(DO_FIELD_NAME)
            !result && action.hasNonNull(ELSE_FIELD_NAME) -> action.get(ELSE_FIELD_NAME)
            else -> null
        }?.let { block ->
            val sequence = block.get(SEQUENCE_FIELD_NAME)?.asText()
            val args = nodeToMap(block.get(ARGS_FIELD) ?: EMPTY_OBJECT)
            blockArgs?.let { bindVars(args, it) }
            context.globalArgs.setAll<ObjectNode>(objectToNode(args) as ObjectNode)
            sequence?.let { context.push(StackFrame.create(it, -1)) }
        }
    }

    private fun loadImports(context: Context) {

        context.script
            .import()
            ?.filter { it.hasNonNull(ACTION_FIELD_NAME) }
            ?.filter { it.hasNonNull(URL_FIELD_NAME) }
            ?.forEach { node ->
                val action = node.get(ACTION_FIELD_NAME).asText()
                val url = node.get(URL_FIELD_NAME).asText()
                val reload = node.get(RELOAD_FIELD_NAME)?.asBoolean() ?: false
                if (reload || actionMap.contains(action).not()) {
                    logger.debug { "fetching action [$action] from [$url]" }
                    register(action, Actions.from(url))
                }
            }
    }
}