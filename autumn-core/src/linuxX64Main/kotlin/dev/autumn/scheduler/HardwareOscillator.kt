package dev.autumn.scheduler

import dev.autumn.os.autumn_rdtsc
import dev.autumn.os.autumn_pause
import dev.autumn.os.autumn_pin_to_core
import platform.posix.sched_yield
import platform.posix.usleep
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.pthread_create
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import platform.posix.pthread_tVar
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.asStableRef

@OptIn(ExperimentalForeignApi::class)
actual class HardwareOscillator actual constructor(
    private val scheduler: AutumnScheduler
) {
    private val runFlag = AtomicInt(0)
    private var threadRef: StableRef<HardwareOscillator>? = null

    actual fun start(coreId: Int) {
        if (!runFlag.compareAndSet(0, 1)) return
        
        threadRef = StableRef.create(this)
        
        val threadId = nativeHeap.alloc<pthread_tVar>()
        // We pack the coreId into the StableRef pointer just for spawning, normally we'd allocate a struct 
        // to pass multiple args but since the class captures `coreId` we could just pass `this`.
        val args = Pair(threadRef!!, coreId)
        val argsRef = StableRef.create(args)
        
        pthread_create(threadId.ptr, null, staticCFunction { argPtr -> 
            val passedArgs = argPtr!!.asStableRef<Pair<StableRef<HardwareOscillator>, Int>>().get()
            val osc = passedArgs.first.get()
            val core = passedArgs.second
            
            if (core >= 0) {
                val result = autumn_pin_to_core(core)
                if (result == 0) {
                    println("[Autumn OS] Oscillator successfully locked to physical Core ID: $core.")
                } else {
                    println("[Autumn OS] ERROR: Extracted thread affinity failed (Error Code: $result). Execution latency is no longer deterministic.")
                }
            }
            
            val targetCyclesPerTick = 200UL
            var lastCycle = autumn_rdtsc()

            while (osc.runFlag.value == 1) {
                val currentCycle = autumn_rdtsc()
                if (currentCycle - lastCycle >= targetCyclesPerTick) {
                    osc.scheduler.tick()
                    lastCycle = currentCycle
                } else {
                    osc.applyIdleStrategy()
                }
            }
            
            passedArgs.first.dispose()
            argPtr.asStableRef<Pair<StableRef<HardwareOscillator>, Int>>().dispose()
            null
        }, argsRef.asCPointer())
    }

    private fun applyIdleStrategy() {
        when (scheduler.powerMode) {
            PowerMode.ACTIVE -> {
                // Direct call to assembly `PAUSE`
                autumn_pause()
            }
            PowerMode.YIELDING -> {
                // Give up Linux scheduler quantum
                sched_yield()
            }
            PowerMode.SLEEPING -> {
                // Very deep C-states: 1 millisecond sleep
                usleep(1000U)
            }
        }
    }

    actual fun stop() {
        runFlag.compareAndSet(1, 0)
    }
}