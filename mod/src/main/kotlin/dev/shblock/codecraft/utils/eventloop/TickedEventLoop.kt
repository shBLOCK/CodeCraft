@file:OptIn(InternalCoroutinesApi::class)
@file:Suppress("unused")

package dev.shblock.codecraft.utils.eventloop

import dev.shblock.codecraft.utils.findMemberFunction
import dev.shblock.codecraft.utils.makeAccessible
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.ThreadSafeHeap
import kotlinx.coroutines.internal.ThreadSafeHeapNode
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.sign
import kotlin.reflect.full.primaryConstructor

private val BEL = Class.forName("kotlinx.coroutines.BlockingEventLoop").kotlin
private val ELIB = Class.forName("kotlinx.coroutines.EventLoopImplBase").kotlin
private val BEL_dispatch = BEL.findMemberFunction<Unit>("dispatch")
private val BEL_enqueueImpl = ELIB.findMemberFunction<Boolean>("enqueueImpl").apply { makeAccessible() }
private val BEL_processNextEvent = BEL.findMemberFunction<Long>("processNextEvent")
private val BEL_scheduleResumeAfterDelay = BEL.findMemberFunction<Unit>("scheduleResumeAfterDelay")

@ApiStatus.Internal
abstract class TickedEventLoop(private val thread: Thread) : CoroutineDispatcher(), Delay {
    abstract val currentTick: Int

    // This eventloop should be strictly FIFO
    private val bel: Any = BEL.primaryConstructor!!.call(thread)

    private val tickTaskCounter = AtomicLong(Long.MIN_VALUE)

    private val tickTaskHeap = ThreadSafeHeap<TickTask>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        BEL_dispatch.call(bel, context, block)
    }

    private fun dispatchCurrentTickTasks() {
        val tick = currentTick
        while (true) {
            val task = tickTaskHeap.removeFirstIf {
                if (it.tick < tick) {
                    when (it) {
                        is TickResumeTask ->
                            // this also calls dispose
                            it.cont.cancel(
                                IllegalStateException("The task is scheduled in the past, scheduled=$it.tick, current=$tick")
                            )

                        else -> throw NotImplementedError()
                    }
                }

                it.tick == tick
            } ?: break
            BEL_enqueueImpl.call(bel, task)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun processNextEvent(tickTasks: Boolean = true): Boolean {
        check(Thread.currentThread() == thread) { "processNextEvent() can only be called on the eventloop's thread" }

        if (tickTasks) dispatchCurrentTickTasks()
        return BEL_processNextEvent.call(bel) <= 0
    }

    fun processAllImmediate(tickTasks: Boolean = true) {
        while (processNextEvent(tickTasks)) {
            /* no-op */
        }
    }

    @ExperimentalCoroutinesApi
    override fun limitedParallelism(parallelism: Int): CoroutineDispatcher {
        return this
    }

    internal fun scheduleResumeOnTick(tick: Int, continuation: CancellableContinuation<Unit>) {
        TickResumeTask(tickTaskHeap, tick, tickTaskCounter.getAndIncrement(), continuation)
            .also { task ->
                tickTaskHeap.addLast(task)
                continuation.disposeOnCancellation(task)
            }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        BEL_scheduleResumeAfterDelay.call(bel, timeMillis, continuation)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        throw NotImplementedError("invokeOnTimeout() is not implemented")
    }

    private abstract class TickTask(
        override var heap: ThreadSafeHeap<*>?,
        val tick: Int,
        val ordinal: Long
    ) : ThreadSafeHeapNode, Runnable, Comparable<TickTask>, DisposableHandle {
        override var index: Int = -1

        override fun compareTo(other: TickTask): Int {
            val dTick = tick - other.tick
            return if (dTick != 0) {
                dTick.sign
            } else {
                (ordinal - other.ordinal).sign
            }
        }

        override fun dispose() {
            @Suppress("UNCHECKED_CAST")
            (heap as? ThreadSafeHeap<TickTask>)?.remove(this)
        }

        override fun toString() = "TickTask[tick=$tick, ord=$ordinal]"
    }

    private inner class TickResumeTask(
        heap: ThreadSafeHeap<*>?,
        tick: Int,
        ordinal: Long,
        val cont: CancellableContinuation<Unit>
    ) : TickTask(heap, tick, ordinal) {
        override fun run() {
            @OptIn(ExperimentalCoroutinesApi::class)
            with(cont) { resumeUndispatched(Unit) }
        }

        override fun toString(): String = super.toString() + cont.toString()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun suspendUntilTick(tick: Int) {
    return suspendCancellableCoroutine { cont ->
        val dispatcher = cont.context[CoroutineDispatcher]
        if (dispatcher !is TickedEventLoop) throw IllegalStateException("Dispatcher is not a TickedEventLoop")
        dispatcher.scheduleResumeOnTick(tick, cont)
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun delayTick(delayTicks: Int) {
    require(delayTicks >= 0) { "Delay ticks must not be negative, got $delayTicks" }
    return suspendCancellableCoroutine { cont ->
        val dispatcher = cont.context[CoroutineDispatcher]
        if (dispatcher !is TickedEventLoop) throw IllegalStateException("Dispatcher is not a TickedEventLoop")
        dispatcher.scheduleResumeOnTick(dispatcher.currentTick + delayTicks, cont)
    }
}