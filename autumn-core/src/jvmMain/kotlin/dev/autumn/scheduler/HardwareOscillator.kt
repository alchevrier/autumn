package dev.autumn.scheduler

import kotlin.concurrent.thread
import java.util.concurrent.locks.LockSupport

actual class HardwareOscillator actual constructor(private val scheduler: AutumnScheduler) {

    // JVM thread interruption is a far safer mechanism for external halt signals.
    private var isRunning = false
    private var oscillatorThread: Thread? = null
    private var androidCallbackRef: Any? = null

    actual fun start(coreId: Int) {
        if (isRunning) return
        isRunning = true

        if (AndroidChoreographer.isAndroid) {
            println("[Autumn OS] Activating Android Choreographer VSYNC native hook.")
            androidCallbackRef = try {
                AndroidChoreographer.start(scheduler)
            } catch (e: Throwable) {
                println("[Autumn OS] Failed to bind Choreographer: ${e.message}. Falling back to standard JVM thread.")
                null
            }
            if (androidCallbackRef != null) return
        }

        oscillatorThread = thread(start = true, name = "autumn-oscillator-core-$coreId") {
            if (coreId >= 0) {
                pinToCore(coreId)
            }
            
            // 2GHz baseline assumption (2,000,000,000 cycles per second)
            // A 100ns tick bounded system equates to advancing the FSM every 200 cycles.
            val targetCyclesPerTick = 200L
            
            var lastCycle = try { NativeClock.rdtsc() } catch (e: Throwable) { System.nanoTime() }

            // The unapologetic, non-yielding hardware clock generator.
            // Pacing is perfectly correlated to exact ALU cycle transitions.
            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    val currentCycle = NativeClock.rdtsc()
                    if (currentCycle - lastCycle >= targetCyclesPerTick) {
                        scheduler.tick()
                        lastCycle = currentCycle
                    } else {
                        applyIdleStrategy()
                    }
                } catch (e: Throwable) {
                    // Fallback to imprecise system clocks if the JNI library isn't compiled
                    val currentCycle = System.nanoTime()
                    if (currentCycle - lastCycle >= 100) {
                        scheduler.tick()
                        lastCycle = currentCycle
                    } else {
                        applyIdleStrategy()
                    }
                }
            }
        }
    }

    private fun applyIdleStrategy() {
        when (scheduler.powerMode) {
            PowerMode.ACTIVE -> {
                // Emits the x86 `PAUSE` instruction. 
                // We intentionally burn 100% of the core to avoid OS sleep states, 
                // but `PAUSE` prevents pipeline speculation saturation and thermal 
                // throttling, which would otherwise slow down our actual execution!
                Thread.onSpinWait()
            }
            PowerMode.YIELDING -> {
                // Yield the OS quantum slice entirely. Better thermal savings if 
                // network congestion is generally relaxed.
                Thread.yield()
            }
            PowerMode.SLEEPING -> {
                // 1 Millisecond hard park. Takes the core into low C-States.
                // Lowest power utilization available for the architecture.
                LockSupport.parkNanos(1_000_000L)
            }
        }
    }

    actual fun stop() {
        isRunning = false

        if (androidCallbackRef != null) {
            try {
                AndroidChoreographer.stop(androidCallbackRef!!)
            } catch (e: Throwable) {
                // Ignore reflections errors on stop
            }
            androidCallbackRef = null
            return
        }

        // Interruption bypasses the standard variable read boundary
        // and guarantees wake-up if the thread is suspended in `SLEEPING` PowerMode (parkNanos).
        oscillatorThread?.interrupt()
        oscillatorThread?.join()
    }

    private fun pinToCore(coreId: Int) {
        // Enforce maximum JVM-level priority directly
        Thread.currentThread().priority = Thread.MAX_PRIORITY
        
        try {
            // Utilize our Native C-Interop to physically lock the Linux thread to the CPU core
            val result = NativeClock.pinToCore(coreId)
            if (result == 0) {
                println("[Autumn OS] Oscillator successfully locked to physical Core ID: $coreId.")
            } else {
                println("[Autumn OS] ERROR: Extracted thread affinity failed (Error Code: $result). Execution latency is no longer deterministic.")
            }
        } catch (e: UnsatisfiedLinkError) {
            println("[Autumn OS] ERROR: Native C library missing. Thread affinity fallback simulated, but hardware isolation is unverified.")
        }
    }
}
