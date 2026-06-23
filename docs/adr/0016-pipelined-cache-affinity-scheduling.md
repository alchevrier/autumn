# ADR 0016: `@Pipelined` Data-Oriented Design and Cache-Affinity Scheduling

## Status
Proposed

## Context

In our earlier JMH benchmarks modeling a Best Bid/Offer (BBO) hot-path, we proved that zero-allocation is not enough. A standard `HashMap` hovered around ~7ms per 1M transitions, while a naive flat array (16MB) thrashed the L1 cache and ballooned to ~71ms. It was only when we structurally bounded the data into a tightly packed L1 matrix that we achieved CPU-bound performance of **1.6ns (p50)**.

However, writing code this way—manually managing parallel arrays, offsets, and array indices—is terrible for developer productivity. It breaks Domain-Driven Design (DDD), introduces massive room for human error (e.g., off-by-one index arithmetic), and prevents AI coding assistants from reasoning about the domain logic safely.

Furthermore, in a standard operating system, the event scheduler’s primary job is *fairness* (thread allocation). In High-Frequency Trading (HFT) and ultra-low-latency systems, we don't care about fairness; we care about **saturating the CPU execution ports**. A standard FIFO event loop destroys hardware caches because consecutive events access entirely disparate areas of memory.

We need a way to automate Data-Oriented Design (DoD) without sacrificing developer ergonomics, and a scheduler that dynamically reorders instructions to glide along the L1 cache.

## Decision

We will introduce the `@Pipelined` compiler mechanism and a Cache-Affinity Scheduler (The "Port Optimizer").

### 1. The `@Pipelined` Annotation (Automated SoA Generation)

Developers will write standard Kotlin interfaces annotated with `@Pipelined`.

```kotlin
@Pipelined
interface Order {
    var ref: Long
    var shares: Int
    var price: Int
}
```

The Autumn K2 compiler plugin intercepts this AST. It **erases** the object completely at compile-time and translates it into a flat Structure of Arrays (SoA), backed by `ByteArray` to natively support Simple Binary Encoding (SBE). This allows DMA offloads (like DPDK) to write directly into the domain models with zero deserialization.

### 2. The Cache-Affinity Scheduler (Port Optimizer)

At runtime, the router acting over these `@Pipelined` structures will operate as a **CPU Port Optimizer**. 
When a burst of events arrives off the wire (e.g., 100 network packets), the scheduler does not process them sequentially. 

Instead, it:
1. Reads the target "key" (index ID) of each event.
2. Calculates the physical hardware Cache Line that will be touched: `Cache Line = (Base_Address + (Index * Element_Size)) / 64`.
3. **Discriminates and groups the functions** based on cache-line affinity.
4. Pipelines the batch to the CPU execution ports.

All operations targeting Cache Line 10 execute together, followed by Cache Line 11. The hardware prefetcher never stalls.

### The High-Level Synthesis (HLS) Mental Model

This architecture can be best understood as **Software High-Level Synthesis (HLS) for commodity CPUs**. 

When programming an FPGA using tools like Xilinx Vivado HLS, developers write C++ but use pragmas to dictate actual spatial hardware layout:
*   `#pragma HLS array_partition` splits arrays into physical BRAM blocks for parallel access.
*   `#pragma HLS pipeline II=1` structures the logic gates to accept a new input every clock cycle without stalling.

Autumn brings this exact paradigm to standard x86/ARM processors:
*   **Array Partitioning:** `@Pipelined` acts as the partition pragma, structurally splitting an interface into physical Structure of Arrays (SoA) byte arrays that natively map to CPU vector registers and SIMD lanes.
*   **Pipelining (II=1):** The Cache-Affinity Scheduler organizes the function calls so they glide perfectly along the physical L1 cache lines, ensuring the CPU execution ports never stall waiting for main memory.

The core philosophy is identical: the developer writes the mathematical/domain logic, and the compiler/synthesizer translates it into a spatial, hardware-sympathetic physical layout.

### Comparison: OOP vs. Pipelined

**1. Less Optimized (Standard OOP / Standard Scheduler):**
*   **Data Layout:** Array of `class Order(val ref: Long, ...)`.
*   **Memory Path:** Pointer chasing in the heap.
*   **Scheduler:** FIFO. Event 1 touches memory `0xA00`, Event 2 touches `0xB50`.
*   **Result:** Constant L1 cache misses. CPU stalls waiting for main memory. Pipeline bubbles.

**2. Highly Optimized (`@Pipelined` SoA + Port Optimizer):**
*   **Data Layout:** Developer writes `interface Order`, compiler generates contiguous `ByteArray`s. (Native SBE).
*   **Memory Path:** Sequential vector bytes.
*   **Scheduler:** Receives Events 1..100. Reorders them so all updates to memory `0xA00-0xA3F` (Cache Line 1) happen consecutively. 
*   **Result:** Branchless, cache-aligned execution. CPU execution ports are kept 100% saturated.

## Consequences

*   **Positive:** Massively improved developer ergonomics. You write standard interfaces; the compiler handles the hardware structure.
*   **Positive:** Perfect synergy with AI coding assistants. AI writes standard domain interfaces, and Autumn handles the underlying micro-architecture.
*   **Positive:** Unlocks the final step in the HFT adoption proof—competing directly with hand-tuned C++ without the mental overhead.
*   **Negative:** Heavily reliance on K2 compiler IR tree manipulation. The complexity is shifted entirely from the developer to the compiler authors (us).

## Related ADRs
*   [ADR 0014: Kotlin Native HFT Pipeline and Thread Pinning](0014-kotlin-native-hft-pipeline-and-thread-pinning.md)
*   [ADR 0015: Universal Circuit Programming (KMP)](0015-kmp-unification-for-universal-circuit-programming.md)
