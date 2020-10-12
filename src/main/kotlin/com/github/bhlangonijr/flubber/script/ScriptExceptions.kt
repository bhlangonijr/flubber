package com.github.bhlangonijr.flubber.script

class ActionNotFoundException(message: String?) : Exception(message)
class NotValidObjectException(message: String?) : Exception(message)
class SequenceNotFoundException(message: String?) : Exception(message)
class ScriptException(message: String?, cause: Throwable) : Exception(message, cause)
class NotHandledScriptException(message: String?, cause: Throwable) : Exception(message, cause)
class ScriptStackOverflowException(message: String?) : Exception(message)
class ExtensionNotSupportedException(message: String?) : Exception(message)
class ScriptStateException(message: String?) : Exception(message)