package dev.autumn.scheduler

external fun requestAnimationFrame(callback: (Double) -> Unit): Int
external fun cancelAnimationFrame(handle: Int)

actual class HardwareOscillator actual constructor(private val scheduler: AutumnScheduler) {

    private var isRunning = false
    private var frameId: Int = 0

    actual fun start(coreId: Int) {
        if (isRunning) return
        isRunning = true
        tickLoop(0.0)
    }

    private fun tickLoop(timestamp: Double) {
        if (!isRunning) return
        scheduler.tick()
        
        // In WebAssembly, we yield to the browser's display matrix clock.
        // Thanks to @Speculative bounding, the burst logic executes optimally 
        // within the 16.6ms physical frame limits allowed to the Wasm Runtime.
        frameId = requestAnimationFrame(::tickLoop)
    }

    actual fun stop() {
        isRunning = false
        cancelAnimationFrame(frameId)
    }
}
