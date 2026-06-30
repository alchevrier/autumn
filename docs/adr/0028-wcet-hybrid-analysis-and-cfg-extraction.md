# ADR 0028: Hybrid WCET Certification, CFG Extraction, and Jitter Profiling

## Status
Accepted

## Context
As Autumn transitions toward formal RTOS Audit Certification (ADR-0026), the current compiler static analysis (`CycleBudgetVisitor`) faces mathematical and architectural limitations common to modern superscalar processors (e.g., x86_64, ARM Cortex-A):

1. **Infeasible Paths via Pure Addition:** Adding up the maximum Kotlin IR node cost over-estimates Worst-Case Execution Time (WCET) because it assumes all branches might execute, even if they are mutually exclusive in the domain logic.
2. **Unbounded Loops:** Without strict loop bounds, a true static WCET cannot be computed.
3. **Hardware Timing Anomalies (The Domino Effect):** On superscalar CPUs, a local best-case scenario (e.g., a cache hit) can trigger a global worst-case pipeline stall due to execution port scheduling contention. A purely static instruction adder cannot foresee this without a perfect proprietary microarchitecture model.
4. **Tooling Visibility:** The IDE Performance Center currently lacks visibility into the branching paths inside an execution handler. 

To overcome these, we cannot rely solely on static pipeline modeling (which requires NDA-restricted chip documentation). Instead, we must bring in structural flow analysis combined with empirical hardware traces.

## Decisions

### 1. Control Flow Graph (CFG) Extraction & Infeasible Path Solver
The K2 Compiler plugin will be expanded to extract the Control Flow Graph (CFG) for the body of any `@Observe` handler. 
* By modeling basic blocks and their control edges, we can utilize a lightweight Integer Linear Programming (ILP) or constraint solver to eliminate "infeasible paths" (e.g., `if (state == A)` and `if (state == B)` mutually excluding each other). 
* **Tooling Synergy:** This CFG will be serialized into `topology.json`. The Autumn IDE Performance Center will use this to render exact branching paths, showing developers the static cost of the `if` vs the `else` logic directly inside the visual node footprint.

### 2. Strict Loop Bounding
The compiler will reject any unbounded loops inside the hot path. Iterations must be bounded by compile-time constants, ring buffer burst sizes, or explicit `@MaxIterations(N)` annotations.

### 3. Move to Hybrid WCET Measurement
Because fully modeling a Zen 4 or Skylake pipeline is mathematically prohibitive, the `autumn-certifier` Gradle plugin will use **Hybrid WCET** for its final certification seal.
* **Mechanism:** The certifier will take the CFG and static cycles computed by the compiler, and overlay them with empirical physical traces gathered during automated build benchmarking using hardware-level tools (e.g., Linux `perf` reading Intel PT (Processor Trace) or ARM CoreSight).
* **Result:** This bridges the gap between mathematically sound loop boundaries and the physical reality of hardware timing anomalies, yielding a cryptographically signable, provable WCET bound without needing access to proprietary silicon blueprints.

## Consequences
* **Positive:** The IDE Performance Center will gain massive visual upgrades by plotting branching weights, taking insights beyond sequential blocks.
* **Positive:** Developers receive tight, realistic WCET limits instead of wildly pessimistic summations.
* **Positive:** Bypasses the need for a multi-million-dollar internal hardware simulation model, relying instead on physical hardware measurements orchestrated safely via standard pipeline tools (`perf`).
* **Negative:** Achieving a certified build will require running the test suite on the specific target hardware (e.g., you cannot sign an official x86_64 performance certificate on an Apple M3 MacBook), though compilation and IDE feedback remain strictly cross-platform.

## Limitations & Future Work
While the hybrid compiler-driven approach scales brilliantly for general high-performance code, formal aerospace or medical-grade auditing will point out the following ongoing execution gaps:

1. **The "Test Vector" Blind Spot:** Empirical `perf` tracing only measures what the dynamic benchmark data feeds it. If our benchmark inputs only trigger the "happy path", the trace misses the worst-case branch. Future iterations of the certifier must generate "Maximum Coverage Test Vectors" to forcefully visit every CFG branch.
2. **The OS Jitter Trap:** Running `perf` on standard Linux inherently measures OS noise (network interrupts, scheduler ticks). True verification must occur on `PREEMPT_RT` kernels or fully isolated CPUs (via `isolcpus` and `nohz_full`).
3. **Hardware Cache Bias (Warm vs. Cold):** Benchmarks naturally warm the L1/L2 caches and prime the branch predictor, producing artificially low cycle counts (Average-Case execution rather than Worst-Case). An auditor would demand `clflush` instructions between passes to prove the absolute maximum cold-cache latency.
4. **Mathematical Solver Maturation:** The current system isolates branches, but the Integer Linear Programming (ILP) mathematical matrix solver to strictly define path exclusivity is not yet implemented. Native path summation is currently acting as a naive upper bound.
