package dev.autumn.scheduler

import dev.autumn.xdp.autumn_rdtsc
import dev.autumn.xdp.autumn_pause
import dev.autumn.xdp.autumn_pin_to_core
import kotlin.native.concurrent.Worker
import platform.posix.sched_yield
import platform.posix.usleep
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual class HardwareOscillator actual constructor(
    private val scheduler: AutumnScheduler
) {
    private val runFlag = AtomicInt(0)
    private var worker: Worker? = null

    actual fun start(coreId: Int) {
        if (!runFlag.compareAndSet(0, 1)) return
        
        // Spawn the dedicated Native thread
        worker = Worker.start(name = "autumn-oscillator-core-$coreId")
        
        worker!!.execute(kotlin.native.concurrent.TransferMode.SAFE, { this to coreId }) { (osc, core) ->
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
        }
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
        if (runFlag.compareAndSet(1, 0)) {
            worker?.requestTermination(processScheduledJobs = false)
        }
    }
}