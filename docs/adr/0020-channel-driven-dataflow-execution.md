# ADR 0020: Channel-Driven Dataflow Execution

## Status
Accepted

## Context
In traditional software architecture, execution is driven imperatively: a developer creates a thread, writes a `while(true)` loop, and explicitly calls functions to process data, blocking or yielding to the OS when no data is available. This introduces severe overhead in low-latency systems: context switching, lock contention, OS scheduler jitter, and cache invalidation.

We established in previous benchmarks that avoiding the OS scheduler and cross-core locking enables single-digit microsecond latencies on the JVM. To achieve this system-wide, we must prevent developers from writing imperative thread loops entirely. 

If we map software to hardware (FPGA/ASIC) principles, execution isn't driven by imperative instruction pointers; it is driven by the flow of data across wires (Channels) triggered by a clock (the Arbiter).

## Decision
We will completely eliminate user-space threads and imperative control flow loops in the Autumn architecture. Execution will be entirely **event-driven via Channels**, mirroring hardware dataflow.

1. **Channels as the Primary Primitive**: Developers instantiate `Channel` objects (representing physical memory ring buffers) with an assigned `weight` (priority/bandwidth).
2. **Topology over Control Flow**: Developers connect Stateful Components (Finite State Machines) to Channels. There are no explicit thread pools or Coroutine Dispatchers in user-space.
3. **Automatic Arbiter Synthesis**: When the system boots, Autumn analyzes the Channel weights and synthesizes an unrolled, lock-free polling schedule (the Arbiter). 
4. **Hot vs. Cold Arbiters (Dispatchers)**: Arbiters themselves have distinct execution profiles based on their assigned role:
   - **Hot Arbiters**: Pinned to isolated physical cores. Spin-wait at 100% CPU utilization. Never yield to the OS. Execute the latency-critical pipelines (e.g., Network, Matching Engine).
   - **Cold Arbiters**: Running on shared/unisolated cores. Allowed to park, sleep, or yield to the OS. Handle non-critical paths like the UI dispatcher, disk I/O, or background telemetry.
5. **Dataflow Execution**: The only way a developer triggers work is by pushing data into a Channel. The synthesized Arbiter continuously pulses the Channels according to their weights, executing the connected state machines inline without context switches.

### Example User Model (Financial Engine)
```kotlin
// 1. Definition (The Wires)
val networkChannel = Channel<NetworkFrame>(weight = 100)
val sessionChannel = Channel<SessionMessage>(weight = 10)
val coldChannel = Channel<Telemetry>(weight = 1)

// 2. Wiring (The Circuit)
networkChannel.connect(orderBook)
sessionChannel.connect(riskEngine)
coldChannel.connect(metricsDb)

// 3. Execution triggers by push
networkChannel.push(frame) // Arbiter naturally picks this up on next clock pulse
```

### Example User Model (Game Engine)
In a game architecture, explicit game loops (`while(running) { update(); render(); }`) are completely eliminated. Instead, the engine is constructed via piped channels:

```kotlin
// 1. Definition (The Wires)
val computeChannel = Channel<PhysicsEvent>(weight = 60) // High frequency physics
val renderChannel  = Channel<RenderEvent>(weight = 16)  // 60FPS target
val inputChannel   = Channel<MotionEvent>(weight = 1)   // Async player input

// 2. Wiring (The Circuit)
// Inputs mutate entity trajectories
inputChannel.connect(physicsEngine)

// The physics boundaries drop resolved entity mutations into the compute pipe
computeChannel.connect(collisionSystem)

// Validated entities are pushed into the graphics pipeline
collisionSystem.onOutputs { validEntities ->
    renderChannel.push(validEntities)
}
renderChannel.connect(gpuCanvasPainter)
```
In this scenario, the synthesis engine scales the polls organically. Player inputs trigger physics updates; computed positions are placed into the `renderChannel`. The Arbiter pulses the GPU drawing commands downstream. If physics lags, it does not block the Arbiter—the pipeline keeps flowing, natively mapping ECS (Entity Component System) principles to Wait-Free channels.

## Consequences

### Positive
- **Temporal Synchronization (Zero Locks)**: Because the Arbiter processes Channels sequentially on a single thread, *temporality* replaces *locking*. Even state mutations (like updating an Order Book) do not require Seqlocks or volatile variables internally. The schedule guarantees exclusivity. Synchronization (via wait-free SPSC ring buffers) only exists at the absolute boundary of the hardware partition.
- **Aggressive Pipelining via Hash Routing**: Because internal state is lock-free and isolated to a specific Arbiter/Core, work can be sharded aggressively. An inbound socket listener can apply MurmurHash3 to incoming session IDs or symbol tickers and statically route them to the SPSC channel of a specific Arbiter. This guarantees sequence ordering and core-affinity without shared state.
- **Zero Context Switching**: The core runs a tight `jmp` polling loop. It never yields to the OS.
- **Mechanism vs. Policy Separation**: Autumn provides the polling mechanism; the developer defines the policy via Channel weights. A database might weigh `ColdChannel` at 100, while an HFT engine weighs `NetworkChannel` at 100.
- **Mental Model Shift**: Developers stop thinking about "threads executing instructions" and start thinking about "events mutating state in a pipeline", forcing cleaner, more decoupled Code.

### Negative
- Eradicates traditional procedural debugging (you cannot easily step-through a `while` loop that doesn't exist in user code).
- Developers must strictly adhere to non-blocking semantics within their State Machine handlers. A single `Thread.sleep()` will stall the entire physical core pipeline.
