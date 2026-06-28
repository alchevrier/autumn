# ADR-0023 — Zero-Allocation Observatory Telemetry

## Status
Proposed

## Context
Standard performance monitoring and telemetry tools (such as JVMTI, sampling profilers, AspectJ wrappers, or Datadog/NewRelic APM agents) are fundamentally incompatible with sub-microsecond HFT architecture. They typically introduce object allocation on the heap to track spans, invoke time-sensing OS system calls, and suffer from lock contention when aggregating metrics across threads.

When a core pipeline evaluates a deterministic frame in ~30 nanoseconds, introducing a standard tracing span that takes 5,000 nanoseconds and triggers a GC pause completley ruins the pipeline. We need a way to measure exact cycle percentiles (P99/P99.9) in production environments without disturbing the measured hot-path.

## Decision
We will introduce the `autumn-observatory` module and expand the K2 compiler's synthesis capabilities to provide zero-allocation structural telemetry.

1. **`@Observe` Annotation**: Developers can tag any hot-path FSM block or topology handler with `@Observe(name = "MetricName")`.
2. **`ObservatorySynthesisTransformer`**: The K2 compiler will intercept this annotation during AST lowering. It will rewrite the target function to prepend and append raw hardware clock counters (e.g., `NativeClock.rdtsc()`). 
3. **Implicit `@ColdChannel` Emitting**: Instead of processing those timestamps directly or allocating a metric span, the injected bytecode will push the raw integers into an implicitly generated lock-free, zero-copy `@ColdChannel` bound to the `AutumnMemoryBank`.
4. **Zero-Pause Snapshotting:** Because the memory layout is purely a native byte-buffer (`AutumnMemoryBank`), developers can trigger asynchronous buffer snapshotting (`memcpy` or `mmap`) without locking the CPU or traversing object pointers. This permits reconstructing the absolute state of the FSM at exact cycle markers for post-mortem "point-in-time" debugging, completely bypassing the JVM's "Stop-The-World" heap dump mechanics.
5. **Out-of-band Processing**: The application can attach an independent `Arbiter` to that cold channel. Operating on a completely different CPU core (potentially as a separate process attached via shared memory), this slow consumer will drain the timestamps, calculate dynamic latency histograms (P50/P99/Max), and flush output to the network/log without the hot-path ever blocking.

## Consequences
- **Positive:** True production observability without observer effect latency jitter. The hot-path pays only the cost of two register-based `rdtsc` read instructions and a non-blocking cache-aligned array write (< 5 nanoseconds total overhead).
- **Positive:** Leverages our existing Data-Oriented abstractions (`@ColdChannel`, `AutumnMemoryBank`), proving their power as a generalized architectural mechanism.
- **Negative:** Requires careful coordination of global memory offsets to ensure the dynamically generated Observatory buffers don't crowd out L2/L3 cache bounds intended for primary business logic.