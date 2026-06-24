package dev.autumn.scheduler

actual class HardwareOscillator actual constructor(private val scheduler: AutumnScheduler) {

    private var isRunning = false
    private var timeoutId: Any? = null

    actual fun start(coreId: Int) {
        if (isRunning) return
        isRunning = true
        tickLoop()
    }

    private fun tickLoop() {
        if (!isRunning) return
        scheduler.tick()
        
        // JS is single-threaded and relies strictly on the Async Event Loop.
        // Power modes basically adapt the timeout request to be friendlier to the browser.
        val sleepTime = when(scheduler.powerMode) {
            PowerMode.ACTIVE -> 0
            PowerMode.YIELDING -> 1
            PowerMode.SLEEPING -> 100
        }
        
        timeoutId = js("setTimeout(this.tickLoop.bind(this), sleepTime)")
    }

    actual fun stop() {
        isRunning = false
        js("clearTimeout(this.timeoutId)")
    }
}
