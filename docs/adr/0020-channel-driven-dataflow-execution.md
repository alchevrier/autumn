# ADR 0020: Channel-Driven Dataflow Execution and Thermal Dynamics

## Status
Accepted

## Context
In traditional software architecture, execution is driven imperatively: a developer creates a thread, writes a `while(true)` loop, and explicitly calls functions to process data, blocking or yielding to the OS when no data is available. This introduces severe overhead in low-latency systems: context switching, lock contention, OS scheduler jitter, and cache invalidation.

We established in previous benchmarks that avoiding the OS scheduler and cross-core locking enables single-digit microsecond latencies on the JVM. To achieve this system-wide, we must prevent developers from writing imperative thread loops entirely. 

If we map software to hardware (FPGA/ASIC) principles, execution isn't driven by imperative instruction pointers; it is driven by the flow of data across wires (Channels) triggered by a clock (the Arbiter).

Furthermore, we must formalize the **Thermal Dynamics** of these channels. Not all data flowing through a system requires the same latency guarantees. Mixing slow data (telemetry) with fast data (market ticks) natively destroys pipeline performance due to cache pollution and buffer bloat.

## Decision
We will completely eliminate user-space threads and imperative control flow loops in the Autumn architecture. Execution will be entirely **event-driven via Channels**, mirroring hardware dataflow. We will strictly categorize these channels into architectural boundaries based on their Thermal Flow.

### The Thermal Flow Taxonomy

#### 1. Hot ➔ Hot (The Data Plane / Multiplexing)
* **Purpose:** Core business execution. Sharding, multiplexing, and pipeline staging.
* **Component:** `@BoundaryChannel` / `@RegisterChannel`
* **Mechanics:** Strict, pre-allocated SPSC (Single-Producer Single-Consumer) queues with 64-byte L1 cache-line padding. If the pipeline backs up, the system must apply mechanical backpressure or drop packets (load shedding), because both ends are running at maximum CPU frequency (`~29ns` handoffs).
* **Example:** A raw AF_XDP network socket (Hot) feeding a FIX parser (Hot), which hashes the stock symbol and drops it into 4 parallel Order Book matching engines (Hot).

#### 2. Cold ➔ Hot (Configuration / Control Plane)
* **Purpose:** Injecting state or limits into the fast path without stalling it.
* **Component:** `SessionChannel` (Concept) / Config Bound Channels
* **Mechanics:** The Cold producer writes to the channel sporadically. The Hot Arbiter polls it once per `tick()` sweep. If there's new config, it updates the local primitive arrays. If not, it moves on instantly. The Hot path never waits for the Cold path.
* **Example:** Updating a risk limit, adding a new stock symbol, or negotiating a new UI theme configuration.

#### 3. Hot ➔ Cold (Observatory / Logging)
* **Purpose:** Extracting state, telemetry, and audits from the fast path without slowing it down (Zero Observer Effect).
* **Component:** `@ColdChannel`
* **Mechanics:** A "Fire and Forget" buffer. The Hot path pushes data in. If the Cold consumer (the logger/disk writer) is too slow and the buffer fills up, the Hot path **must overwrite or drop the telemetry** rather than blocking the trade or frame render. 
* **Example:** The `autumn-observatory` timestamp dumping, writing trade executions to disk, or sending UI analytics.

### Boundary Channels
It is also established that `@BoundaryChannel` conceptually represents a broader **`BoundaryChannel`**. A system does not just have a "Network" device. It may have incoming Dataflow bounds from AF_XDP queues, persistent UDP devices, WebSockets, or UI Touch Event queues. All of these external hardware integrations classify as `BoundaryChannels` feeding into the internal `Hot ➔ Hot` Data Plane.

### Execution mechanics

1. **Channels as the Primary Primitive**: Developers instantiate `Channel` objects (representing physical memory ring buffers) with an assigned `weight` (priority/bandwidth).
2. **Topology over Control Flow**: Developers connect Stateful Components (Finite State Machines) to Channels. There are no explicit thread pools or Coroutine Dispatchers in user-space.
3. **Automatic Arbiter Synthesis**: When the system boots, Autumn analyzes the Channel weights and synthesizes an unrolled, lock-free polling schedule (the Arbiter). 
4. **Hot vs. Cold Profiles (Dispatchers)**: Arbiters themselves have distinct execution profiles based on their assigned role:
   - **Hot Plotted**: Pinned to isolated physical cores. Clocked actively via the `HardwareOscillator`. Never yield to the OS. Execute the latency-critical pipelines (e.g., Network, Matching Engine).
   - **Cold Shared**: Running on shared/unisolated cores. Allowed to park, sleep, or yield to the OS. Handle non-critical paths like the UI dispatcher, disk I/O, or background telemetry.
5. **Dataflow Execution**: The only way a developer triggers work is by pushing data into a Channel. The synthesized Arbiter continuously pulses the Channels according to their weights, executing the connected state machines inline without context switches.

### Example User Model (Financial Engine)
```kotlin
// 1. Definition (The Wires)
val boundaryChannel = Channel<NetworkFrame>(weight = 100)
val sessionChannel = Channel<SessionMessage>(weight = 10)
val coldChannel = Channel<Telemetry>(weight = 1)

// 2. Wiring (The Circuit)
boundaryChannel.connect(orderBook)
sessionChannel.connect(riskEngine)
coldChannel.connect(metricsDb)

// 3. Execution triggers by push
boundaryChannel.push(frame) // Arbiter naturally picks this up on next clock pulse
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
- **Mechanism vs. Policy Separation**: Autumn provides the polling mechanism; the developer defines the policy via Channel weights. A database might weigh `ColdChannel` at 100, while an HFT engine weighs `BoundaryChannel` at 100.
- **Mental Model Shift**: Developers stop thinking about "threads executing instructions" and start thinking about "events mutating state in a pipeline", forcing cleaner, more decoupled Code.

### Negative
- Eradicates traditional procedural debugging (you cannot easily step-through a `while` loop that doesn't exist in user code).
- Developers must strictly adhere to non-blocking semantics within their State Machine handlers. A single `Thread.sleep()` will stall the entire physical core pipeline.
