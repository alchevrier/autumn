# ADR-0019: K2 Compiler IR Injection for Global Struct Pooling

## Status
Accepted (2026-06-24)

## Context
Standard Kotlin/JVM environments rely heavily on the garbage collector and heap-allocated objects for data passing. In high-frequency trading (HFT) and ultra-low latency scenarios, pointer chasing and cache misses create unacceptable p99 tail latencies. While we solved data modeling via the Flyweight Zero Allocation Model (ADR-0017) and ring buffers (ADR-0014), the actual initialization of these channels naturally spawned fragmented arrays across the heap.

We needed a way to statically prove channel capacity layouts (`@ThreadCacheBudget`, capacity alignments) and condense disjoint architectural queues (`@RegisterChannel`, `@NetworkChannel`, `@ColdChannel`) into a unified, contiguous hardware memory pool (`AutumnMemoryBank`).

## Decision
We enforce a literal OS-bypass at the compiler level via a custom Kotlin K2 Compiler Plugin using the new FIR/IR architecture:
1. **Pass 1: Struct Pooling & Bounds Calculation**: Using `IrElementVisitorVoid`, we scan all defined channels and identically align capacities for structs annotated with `@Pipelined`. We concatenate them into a global integer space offset map.
2. **Boot Injection (`MemoryBankInitializationTransformer`)**: At the start of `main()`, we inject `AutumnMemoryBank.allocate(N)` where `N` is the perfectly padded, cache-line aligned byte count across the entire application ecosystem. 
3. **Instancing Injection (`PipelinedSoATransformer`)**: The compiler intercepts the instantiation of user-declared `AutumnChannel` fields and mutates the AST block. Instead of relying on isolated object buffers, the ring buffers natively construct with statically injected `globalIndexOffset` parameters, mapping their read/write pointers strictly into their pre-determined bounds of the native `AutumnMemoryBank`.

## Consequences

### Positive
*   **Hardware Sympathy by Default**: Developers simply declare properties (e.g., `val log = AutumnChannel<OrderEvent>(1024)`). The compiler ensures it behaves as a contiguous chunk of physical memory via pointer-math.
*   **Compiler-Enforced Layout**: Misalignments (like non-power-of-two capacities or cold-channel access inside a hot path) natively fail the build before reaching runtime via our `ThreadCacheBudgetChecker` and FIR checkers.
*   **Zero-Copy Routing**: Hot paths and cold paths operate on identical MemoryBanks. When a hot path emits to an `@ColdChannel` to log to disk, the index passes seamlessly without copying payload bytes across the heap.

### Negative
*   **Compiler Brittleness**: Heavily couples Autumn's build pipeline to internal JetBrains Kotlin K2 IR Abstract Syntax Tree representations. Structural upgrades to Kotlin IR could break the injection sequence.
*   **Loss of Reflection/Debuggability**: Because variables are injected/unrolled directly by the compiler passing structural pointers, standard IDE debuggers evaluating `.quantity` will see flyweight index getters rather than populated heap objects in an inspector view.
