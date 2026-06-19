# ADR-0012: Circuit-Based Data Pipeline and Interrupt Moderation

## Context
Standard reactive mobile applications process data inside "pipes" of heap allocations (e.g., `Network -> ByteArray -> JSON Deserializer -> List<DTO> -> Flow<State> -> Redraw`). This pipeline induces severe CPU wakeups and uncontrolled garbage collection (GC), causing battery drain, dropped UI frames, and Out-Of-Memory (OOM) network avalanches.

In our journey to bring Hardware Sympathy to consumer consumer apps, we established `@InjectBudget` (ADR-0015) to emulate hardware bounds. We needed a way to propagate OS network data through our framework to the UI layer without breaking this zero-allocation, bounded contract.

## Decision
We establish a fixed "Interrupt Coalesced, Zero-Allocation" boundary spanning the entire execution loop, operating purely on integers and arrays just like an FPGA or Data Plane Development Kit (DPDK). 

The pipeline strictly enforces the following components:

### 1. Compile-Time Concurrency Budget
We use `@NetworkConcurrencyBudget(maxInFlightRequests = N)` evaluated by the K2 compiler plugin to statically size a locking array (`NetworkSlotManager`). This acts as an **Instant Circuit Breaker**. If a user or a logical defect spawns unbound requests, the system drops them locally in `O(1)` without heap queuing, preventing OOMs before the OS socket is even touched.

### 2. "Fire and Forget In-Place" Handoff
As formalized by `RequestHandoff`, we do not allocate DTOs upon network resolution. When the OS finishes an HTTP request, its raw byte buffer (`ByteArray`) is passed directly to the `JsonConfigParser`. The parser calculates logical dimension coordinates and writes them directly into the pre-allocated integer matrices of the `StringRegistry` and `ConfigManager`. 

The network execution returns `Result<Unit>`, forcing the system to retrieve data only from the pre-allocated bounds rather than moving intermediate data objects around.

### 3. Emulated Interrupt Moderation (State Engine)
To make the UI reactive without `Observer` allocations, we emulate NIC hardware Interrupt Coalescing using the `EpochStateEngine`. 
- **The Registers:** Each memory slot has a matching index in an Epoch `IntArray` (e.g., index 4 represents slot 4's version).
- **The Interrupt Wire:** A singleton `MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = DROP_OLDEST)` acts as the global hardware pin. 
- **The Moderation:** When the network parser finishes, it increments the slot's Epoch array and pulses the global wire. Because it is a channel dropping the oldest signals, if 50 background operations finish inside a single 16ms frame, the pulses coalesce. The UI thread wakes up **exactly once**.

## Consequences
- **Positive:** We dictate UI CPU C-states (deep sleep) effectively. The battery lifespan of parsing and scrolling large virtual lists improves geometrically.
- **Positive:** UI Rendering is strictly `O(Visible Items)`. The single wakeup allows the UI to check its local cached epoch against the global epoch matrix and repaint exclusively what changed.
- **Negative:** Deep conceptual barrier to entry for standard mobile engineers who expect `LiveData` or structured object parsing models.
