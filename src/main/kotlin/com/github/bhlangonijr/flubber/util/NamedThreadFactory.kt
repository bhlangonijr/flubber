package com.github.bhlangonijr.flubber.util

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger


class NamedThreadFactory(prefix: String) : ThreadFactory {

    private val group: ThreadGroup
    private val threadNumber = AtomicInteger(1)
    private val namePrefix: String

    override fun newThread(runnable: Runnable): Thread {
        val thread = Thread(group, runnable, namePrefix + "-" + threadNumber.getAndIncrement(), 0)
        if (thread.isDaemon) {
            thread.isDaemon = false
        }
        if (thread.priority != Thread.NORM_PRIORITY) {
            thread.priority = Thread.NORM_PRIORITY
        }
        return thread
    }

    companion object {
        private val poolNumber = AtomicInteger(1)
    }

    init {
        val s = System.getSecurityManager()
        group = if (s != null) s.threadGroup else Thread.currentThread().threadGroup
        namePrefix = (prefix + "-" + poolNumber.getAndIncrement() + "-thread-")
    }
}