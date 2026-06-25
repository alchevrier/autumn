package dev.autumn.scheduler.platform

/**
 * Kotlin/JS Implementation of the OS Thread Bridge.
 * 
 * In a browser or Node.js environment, the event loop is strictly single-threaded
 * JS isolates (Web Workers excluded). There is no native API to pause a thread hardware,
 * nor can you pin explicit CPUs via the v8 runtime.
 */
class JsThreadBridge : OSThreadBridge {
    
    override fun yieldThread() {
        // In JS, yielding requires returning control to the browser's Macrotask/Microtask queue.
        // There is no synchronous `yield()` that halts execution inline.
        // Yielding in Kotlin/JS is typically handled async via Coroutines or setTimeout(0).
        // Since Autumn Arbiters are strictly continuous execution blocks, we swallow this inline.
    }

    override fun pauseHardware() {
        // WebAssembly / JS has no instruction mapped to x86 'pause'.
        // We cannot spin-wait tightly in JS without freezing the entire browser tab,
        // so Hot Arbiters in JS realistically must be orchestrated via requestAnimationFrame
        // or WebWorkers if they need continuous simulation loops without UI locking.
    }

    override fun pinToCore(coreId: Int): Boolean {
        // v8 has no concept of CPU core affinity.
        return false 
    }
}
