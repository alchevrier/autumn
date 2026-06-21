# 8. Kotlin Native HFT Pipeline and Thread Pinning

Date: 2026-06-21

## Status
Accepted

## Context
The Autumn framework was originally conceived to eliminate GC pauses and enforce memory budgeting (`@LongLived`) for predictable UI state management via an FSM (Finite State Machine) paradigm. However, benchmarking against standard JVM structures highlighted a profound structural advantage: Autumn natively implements the "Circuit-Based Programming" paradigm on commodity hardware.

In traditional HFT (High-Frequency Trading) environments (typically C++ or Rust), developers mentally juggle cache-lines, false sharing, and SoA (Structure of Arrays) layouts manually. A single misplaced `std::unordered_map` or heap allocation (`new`) by an unwary contributor can silently introduce microsecond tail-latency spikes. 

Conversely, Autumn's architectural constraints (zero-allocation budgets set strictly at boot-time) provide an application-wide contract.

## Decision
We officially recognize and support the use of Autumn as a framework for building bare-metal POSIX C++ tier pipelines via **Kotlin/Native** (LLVM) and **GraalVM Native**. 

To facilitate real HFT feed handler architectures natively:
1. **Compile-Time Contract Enforcement via Custom Budget Annotations:** Autumn will continue to strictly enforce `@LongLived` primitive arrays (SoA). However, in HFT contexts, capacity sizes must be enforced by custom budget annotations (e.g., `@BboMatrixBudget(tickLevels = 10_000, maxOrdersPerLevel = 64)`). The compiler plugin will read these annotations to lock in array boundaries, preventing developers from manually inflating array capacities and blowing out the L1 cache. The framework rejects dynamic memory allocation inside the hot-path event loop natively.
2. **State-Level Thread Pinning & Thread Allocation Budgets:** Because Kotlin Native interops directly with POSIX (`#include <pthread.h>`), we endorse explicit CPU core affinity (`pthread_setaffinity_np`) per FSM State. 
   - **Thread Allocation Budgets:** We introduce cache-level budgets (e.g., `@ThreadCacheBudget(target = CacheLevel.L1)`). The compiler plugin assesses data structures assigned to the state and enforces that the total byte size cannot spill out of the target cache on that pinned thread.
   - **Unannotated Fallback:** If no annotation is explicitly given, the framework defaults to a pessimistic allowance, assuming *all threads* could be impacted and no deterministic cache placement is guaranteed.
   - *Example:* NIC Epoll (State_Decode) pinned to isolated Core 2 (`@ThreadCacheBudget(target = CacheLevel.L2)`).
   - *Example:* Strategy Execution (State_Execute) pinned to isolated Core 3 (`@ThreadCacheBudget(target = CacheLevel.L1)`).
3. **Hot-Path Decoupling:** In alignment with FPGA design principles, the absolute "hot-path" matrix shall only maintain the Best-Bid/Best-Ask (BBO) snapshot in the L1 cache for instant execution routing. Full Order Book manipulation (depth construction, tracking individual REST order identifiers) is delegated entirely off the critical execution path into a parallel state for algorithmic strategies to consume asynchronously.
4. **Compile-Time Wire Synthesis (Function Interception):** To pass data between these isolated, pinned CPU cores safely, Autumn acts as a High-Level Synthesis (HLS) transpiler. Functions annotated with `@Wire(bufferSize = X)` do not execute a standard JVM/LLVM call stack. Instead, the compiler plugin intercepts the invocation, synthesizes an SPSC (Single-Producer Single-Consumer) primitive ring buffer in memory, and rewrites the function invocation into a lock-free buffer append. The receiving class's `@WireReceiver` function is wrapped in a generated polling loop on its designated core. This pipelines event data between functions completely lock-free, avoiding the OS kernel and bypassing GC entirely.

## Consequences
- **Positive:** We achieve C++/Rust-grade deterministic tail-latencies (`p99.99` in microseconds) combined with the developer-friendly syntax and memory safety of Kotlin.
- **Positive:** Latency regressions are prevented at compile-time/framework-initialization-time rather than discovered at runtime, as the budget strictly rejects out-of-bounds object creation.
- **Negative:** Forces developers to rethink data structures entirely in terms of integer offsets and primitive flat-arrays rather than traditional object-oriented classes.