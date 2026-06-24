package dev.autumn.scheduler

actual class HardwareOscillator actual constructor(
    private val scheduler: AutumnScheduler
) {
    actual fun start(coreId: Int) {
        // Linux Native start implementation
    }

    actual fun stop() {
        // Linux Native stop implementation
    }
}