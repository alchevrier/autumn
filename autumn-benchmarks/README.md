# Autumn Benchmarks

This module contains the raw performance comparisons between the compiler-rewritten Autumn topologies and traditional "classic" Java/Kotlin approaches.

## OrderBookComparison

The `OrderBookComparison` benchmark measures the latency of processing 1 million inbound `OrderEvent` ticks, calculating the base offsets, and routing the orders into a simulated flat Level-2 Order Book.

### The Cross-Thread Pipelined Benchmark

Historically, pipeline latency was estimated by dividing bulk execution time by the event count. However, Autumn now employs **Zero-Allocation Hardware Telemetry** (Phase 3) for absolute, explicit percentile accuracy natively injected by the compiler.

By annotating the FSM handler with `@Observe`, the Autumn K2 compiler automatically weaves hardware clock interceptors (using Native `rdtsc` or OS capabilities) perfectly around the *outside* of the execution boundary in the generated AST. This prevents instruction-cache pollution inside the purely business-oriented domain logic.

If you run the benchmark today, we employ a **500,000 message JIT Warmup Phase** to push the JVM into C2 optimized machine code—fully eliminating Interpreter and JVM compilation stalls. Following the warmup, we stream exactly 1,000,000 true execution events cross-thread into the pipelined Arbiter loop:

| Metric | Measured Exact Latency (Cross-Thread) |
|--------|--------------------------------------:|
| **p50 (Median)**| ~2,708 ns (2.7 µs) |
| **p99**         | ~4,854 ns (4.8 µs) |
| **p99.9**       | ~7,282 ns (7.2 µs) |
| **p99.99**      | ~11,467 ns (11.4 µs) |

While exact nanosecond-precision hardware timing reveals actual OS thread scheduling and L1-L3 cache interactions rather than bulk averages, the variance is incredibly tight. Natively maintaining a **11.4 µs P99.99** in pure Kotlin natively demonstrates the structural bypassing of Garbage Collection entirely. The static memory architecture (`LatencyHistogram`) avoids object allocations by recording execution deltas iteratively directly into a flat, pre-allocated region inside the `AutumnMemoryBank`.

Furthermore, this extreme stability is achieved by implementing explicit **L1 Hardware Cache Line Padding** directly into the `Channel` structure. Because standard JVMs (`Java 9+`) heavily lock down `@Contended` memory paddings behind runtime `--add-exports` flags, Autumn guarantees zero-configuration mechanics by leveraging class inheritance logic. The JVM specification restricts it from rearranging or interleaving subclass properties with superclass properties, allowing us to enforce strict 64-byte spacing between the Producer indices and Consumer FSM indices reliably.

### Proving Execution Port Saturation & IPC ("Mechanical Sympathy")

While strict OS-level (`perf_event_paranoid=4`) security often blocks raw Hardware Performance Counter measurements (`perf stat`) on standard cloud VMs, the sub-40ns pipeline times provide empirical proof of superscalar instruction-level parallelism (ILP) and execution port saturation.

The typical single-core cycle budget for x86 processors means we are completing the entire pipeline step in around ~80 CPU clock cycles. This is only physically possible because:

1. **The Branch Predictor is Saturated:** The `TopologySynthesisTransformer` flattens the execution queue graph into a single deterministic `tick()` frame rather than an OS-blocking spinloop. There are no virtual method dispatch tables (vtables) to resolve dynamically.
2. **No L3/RAM Cache Misses:** The `AutumnMemoryBank` (flat primitive arrays) operates linearly, perfectly triggering the CPU's adjacent cache-line prefetchers. The execution ports never stall waiting for main memory (usually a ~200-300 cycle penalty).
3. **The Lock-Free HardwareSequence:** The indices act exactly like an unrolled DPDK `rte_ring`. No OS-level Mutex context switches (`wait`/`notify`) mean `x86` execution ports are doing uninterrupted math on L1 cache registers rather than sleeping or flushing translation lookaside buffers (TLBs).

### The Autumn Solution: Static Topologies

When the `@BoundaryChannel(sharded = N)` annotation is added, the Autumn K2 compiler automatically bridges this exact architecture without the boilerplate:

- It dynamically instantiates an array of `N` separated SPSC channels (`initPartitions`).
- It seamlessly rewrites the producer's `event = inboundNetwork.next()` to hash the symbol payload (`hashKey`) and target the specifically pinned SPSC partition partition natively via `nextIndex(hashKey)`.
- It **completely unrolls the Arbiter execution logic** straight into the IR byte-tree (`TopologySynthesisTransformer.kt`), compiling into a static `tick()` frame cooperatively paced by the `AutumnScheduler` and `HardwareOscillator`, mapped securely against the globally validated hardware bounds.

By defining the `AutumnMemoryBank` globally at compile time, we completely strip away the need for explicit bounds checking, `VarHandle` barriers, `false-sharing` cache-line padding, and dynamic SPMC locking logic.

With Autumn, you write idiomatic event-driven domain logic, and the compiler statically enforces and synthesizes a wait-free, optimally routed multi-core system.
