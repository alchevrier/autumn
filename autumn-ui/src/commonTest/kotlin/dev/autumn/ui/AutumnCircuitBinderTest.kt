package dev.autumn.ui

import dev.autumn.annotations.LongLived
import dev.autumn.config.StringRegistry
import dev.autumn.state.EpochStateEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

@LongLived
class AutumnCircuitBinderTest {

    // Concrete implementation for testing
    class TestBinder(
        stateEngine: EpochStateEngine,
        stringRegistry: StringRegistry
    ) : AutumnCircuitBinder(stateEngine, stringRegistry)

    @Test
    fun `resolveTextPrimitive exactly decodes registered bytes at the glass`() {
        // 1. Setup the pre-allocated string registry simulating a parsed response
        val registry = StringRegistry(10)
        val textBytes = "hello circuit".encodeToByteArray()
        registry.setBuffer(textBytes)
        
        // 2. The parser registered coordinates [0..12]
        val coordinateId = registry.register(0, textBytes.size)

        val engine = EpochStateEngine(10)
        val binder = TestBinder(engine, registry)

        // 3. The UI resolves the string at render-time exactly once
        val result = binder.resolveTextPrimitive(coordinateId)
        assertEquals("hello circuit", result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `attachToInterruptWire coalesces invalidations to minimize renders`() = runTest(StandardTestDispatcher()) {
        val registry = StringRegistry(10)
        val engine = EpochStateEngine(10)
        val binder = TestBinder(engine, registry)

        var invalidateCount = 0
        // Use backgroundScope so runTest doesn't wait for this infinite collect to finish
        binder.attachToInterruptWire(backgroundScope) {
            invalidateCount++
        }

        // 1. Let the flow subscription start
        yield()
        
        assertEquals(0, invalidateCount, "No invalidations until an epoch increments")

        // 2. Simulate the state engine receiving multiple mutated backend slots 
        // in a single frame execution
        engine.incrementEpoch(0)
        engine.incrementEpoch(1)
        engine.incrementEpoch(2)

        // 3. Let the coroutine dispatcher process the global wire SharedFlow pulse
        yield()

        // 4. In a traditional system, 3 changes = 3 invalidates (or Observers mapped).
        // Our interrupt wire coalesced them perfectly into 1!
        assertEquals(1, invalidateCount, "Multiple mutations coalesce into a single invalidation trigger")
    }
}
