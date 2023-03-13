package com.github.bhlangonijr.flubber.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.bhlangonijr.flubber.script.Script
import com.github.bhlangonijr.flubber.script.Script.Companion.MAIN_FLOW_ID
import com.github.bhlangonijr.flubber.script.SequenceNotFoundException
import com.github.bhlangonijr.flubber.util.Util
import java.io.InputStream
import java.net.URL
import java.util.*

class Context private constructor(
    private val data: ObjectNode,
    val script: Script
) : ContextExecutionListener() {

    companion object {

        const val CONTEXT_ID_FIELD = "contextId"
        const val GLOBAL_FIELD = "global"
        const val SCRIPT_FIELD = "script"
        const val STACK_FIELD = "stack"
        const val GLOBAL_ARGS_FIELD = "args"
        const val RESULT_FIELD = "result"
        const val ASYNC_FIELD = "async"
        const val STATE_FIELD = "state"
        const val SEQUENCE_ID_FIELD = "sequenceId"
        const val ACTION_ID_FIELD = "actionId"
        const val EXCEPTION_FIELD = "exception"
        const val SEQUENCE_TYPE_FIELD = "sequenceType"
        const val ELEMENTS_FIELD = "_elements"
        const val MAX_STACK_SIZE = 50
        const val MAIN_THREAD_ID = "mainThreadId"
        const val PATH_FIELD = "path"

        private val mapper = ObjectMapper().registerKotlinModule()

        val EMPTY_OBJECT: ObjectNode = mapper.createObjectNode()

        fun load(source: URL): Context = load(mapper.readTree(source))

        fun load(source: InputStream): Context = load(mapper.readTree(source))

        fun load(source: String): Context = load(mapper.readTree(source))

        fun load(data: JsonNode): Context {
            val script = Script.from(data[SCRIPT_FIELD])
            return Context(data as ObjectNode, script)
        }

        fun create(script: Script, args: String): Context {

            val argsJson = mapper.readTree(args)
            val data = mapper.createObjectNode()
            data.put(CONTEXT_ID_FIELD, UUID.randomUUID().toString())
            data.set<ObjectNode>(SCRIPT_FIELD, script.root)
            data.withObject("/$STATE_FIELD")
                .put(MAIN_THREAD_ID, ExecutionState.NEW.name)
            data.withObject("/$GLOBAL_FIELD")
                .set<ObjectNode>(GLOBAL_ARGS_FIELD, argsJson)
            return Context(data, script)
        }
    }

    val id: String
        get() = data.get(CONTEXT_ID_FIELD).asText()

    val globalArgs: ObjectNode
        get() = data.withObject("/$GLOBAL_FIELD")
            .get(GLOBAL_ARGS_FIELD) as ObjectNode

    val state: ObjectNode
        get() = data.withObject("/$STATE_FIELD")

    val stack: ObjectNode
        get() = data.withObject("/$STACK_FIELD")

    val running: Boolean
        get() = state.fieldNames()
            .asSequence()
            .any { running(it) }

    fun setVariable(path: String, value: JsonNode) {

        synchronized(globalArgs) {
            globalArgs.set<JsonNode>(path, value)
        }
    }

    fun running(threadId: String): Boolean =
        threadStateValue(threadId) == ExecutionState.RUNNING
                || threadStateValue(threadId) == ExecutionState.NEW

    fun threadStateValue(threadId: String): ExecutionState =
        ExecutionState.valueOf(state.get(threadId).asText())

    fun setThreadState(threadId: String, executionState: ExecutionState) {

        synchronized(state) {
            state.put(threadId, executionState.name)
        }
        if (threadId == MAIN_THREAD_ID && executionState == ExecutionState.FINISHED) {
            invokeOnCompleteListeners()
        }
    }

    fun threadStack(threadId: String): ArrayNode = stack.withArray(threadId)

    fun next(threadId: String = MAIN_THREAD_ID): FramePointer? =
        when (threadStateValue(threadId)) {
            ExecutionState.NEW -> {
                script.sequence(MAIN_FLOW_ID)?.let {
                    setThreadState(threadId, ExecutionState.RUNNING)
                    FramePointer(EMPTY_OBJECT, MAIN_FLOW_ID, -1, true)
                } ?: throw SequenceNotFoundException("Sequence [$MAIN_FLOW_ID] not found")
            }
            ExecutionState.RUNNING -> {
                pop(threadId)?.let { frame ->
                    val nextActionIndex = frame.actionIndex + 1
                    val sequence = script.sequence(frame.sequence)
                        ?: throw SequenceNotFoundException("Sequence [${frame.sequence}] not found")
                    when {
                        frame.sequenceType -> FramePointer(EMPTY_OBJECT, frame.sequence, nextActionIndex, true, frame)
                        nextActionIndex < sequence.size() -> script.action(frame.sequence, nextActionIndex)
                            ?.let { action -> FramePointer(action, frame.sequence, nextActionIndex, false, frame) }
                        threadStack(threadId).isEmpty.not() -> next(threadId)
                        else -> {
                            setThreadState(threadId, ExecutionState.FINISHED)
                            null
                        }
                    }
                }
            }
            else -> {
                null
            }
        }

    fun toJson() = toString()

    override fun toString(): String = data.toPrettyString()

    fun push(threadId: String, frame: StackFrame): ArrayNode {

        val stack = threadStack(threadId)
        return synchronized(stack) {
            stack.add(frame.data)
        }
    }

    fun pop(threadId: String): StackFrame? {

        val stack = threadStack(threadId)
        return if (stack.isEmpty.not()) {
            synchronized(stack) {
                StackFrame(stack.remove(stack.size() - 1) as ObjectNode)
            }
        } else {
            null
        }
    }

    fun current(threadId: String): StackFrame? =
        if (threadStack(threadId).isEmpty.not()) StackFrame(threadStack(threadId).last() as ObjectNode) else null

}

data class FramePointer(
    val node: JsonNode,
    val sequenceId: String,
    val actionIndex: Int,
    val sequenceType: Boolean,
    val previousFrame: StackFrame? = null
)

data class StackFrame(val data: ObjectNode) {

    companion object {

        private val mapper = ObjectMapper().registerKotlinModule()

        fun create(
            path: String,
            sequenceId: String,
            actionIndex: Int = -1,
            sequenceType: Boolean = false,
            args: JsonNode = mapper.createObjectNode(),
            result: Any? = null
        ): StackFrame {

            val data = mapper.createObjectNode()
            data.put(Context.PATH_FIELD, path)
            data.put(Context.SEQUENCE_ID_FIELD, sequenceId)
            data.put(Context.ACTION_ID_FIELD, actionIndex)
            data.put(Context.SEQUENCE_TYPE_FIELD, sequenceType)
            val frame = StackFrame(data)
            frame.args = args
            frame.result = result
            return frame
        }
    }

    val path: String
        get() = data.get(Context.PATH_FIELD).asText()

    val sequence: String
        get() = data.get(Context.SEQUENCE_ID_FIELD).asText()

    val actionIndex: Int
        get() = data.get(Context.ACTION_ID_FIELD).asInt()

    val sequenceType: Boolean
        get() = data.get(Context.SEQUENCE_TYPE_FIELD).asBoolean()

    var args: JsonNode
        get() = data.get(Context.GLOBAL_ARGS_FIELD)
        set(value) {
            synchronized(data) {
                data.set<JsonNode>(Context.GLOBAL_ARGS_FIELD, mapper.valueToTree(value))
            }
        }

    var result: Any?
        get() = data.get(Context.RESULT_FIELD)
        set(value) {
            synchronized(data) {
                data.set<JsonNode>(Context.RESULT_FIELD, mapper.valueToTree(value))
            }
        }
}

enum class ExecutionState {
    NEW, RUNNING, WAITING, FINISHED
}