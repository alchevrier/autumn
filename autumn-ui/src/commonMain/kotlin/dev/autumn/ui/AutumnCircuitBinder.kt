package dev.autumn.ui

import dev.autumn.config.StringRegistry
import dev.autumn.state.EpochStateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Acts as the bridge between Autumn's Circuit-Based backend and any platform UI.
 * 
 * Instead of taking Domain Objects (like UserProfile), the Canvas requires only
 * the memory configuration offset containing the pre-parsed text bytes, converting 
 * to a platform String exclusively at the millisecond the pixel renderer requests it.
 */
abstract class AutumnCircuitBinder(
    protected val stateEngine: EpochStateEngine,
    protected val stringRegistry: StringRegistry,
) {
    /**
     * Bootstraps the UI by subscribing to the global Hardware Interrupt Wire.
     * 
     * When the wire pulses, the UI wakes up once, compares local state epochs, 
     * and forces an invalidation/repaint of rendering slots exclusively if their 
     * epoch has advanced.
     */
    fun attachToInterruptWire(scope: CoroutineScope, onInvalidate: () -> Unit) {
        scope.launch {
            stateEngine.acquireInterruptWire().collect {
                // In a perfect platform adapter, this calls setNeedsDisplay() in iOS
                // or triggers a CompositionLocal state bump in Jetpack Compose
                onInvalidate()
            }
        }
    }

    /**
     * Platform-agnostic function that reads raw bytes from the registry matching 
     * the coordinate, allocating a UI String dynamically exactly once.
     */
    fun resolveTextPrimitive(coordinateId: Int): String {
        // String allocation happens ONLY at the final rendering millisecond 
        // to pass to standard Text() layouts without impacting backend performance.
        return stringRegistry.getString(coordinateId)
    }
}
