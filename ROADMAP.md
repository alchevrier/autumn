# Autumn Framework Roadmap

This document outlines the strategic priorities for the Autumn framework, transitioning from a proven core compiler architecture into a fully featured, production-ready, and certifiable ecosystem.

## Phase 1: Compliance & Observability (Current Priority)
**Goal:** Prove the system's safety and provide world-class tooling.
*   [ ] **RTOS Audit Certification (`autumn-certifier`)**: Implement the Gradle plugin that consumes `topology.json` and emits cryptographically signed WCET and Zero-Allocation guarantees (ADR-0026).
    *   *Audit Constraint 1:* Must mathematically account for baseline OS/Hardware jitter (e.g., non-maskable hardware interrupts, SMIs).
    *   *Audit Constraint 2:* Must prove and enforce **Memory Bus Isolation**. The compiler/certifier must guarantee that cold-path execution (e.g., logging) cannot steal DRAM bandwidth, induce L3 cache evictions, or stall the hot path via memory controller saturation.
    *   *Audit Constraint 3:* Must validate against a specific **Microarchitecture Model** (e.g., Zen 4, Skylake, Cortex-R52). WCET cycle counts and memory bandwidth proofs are meaningless without hardware-specific instruction latency tables, execution port geometries, and cache organization models.
    *   *Audit Constraint 4:* Must support **Domain-Specific Jitter Profiles** (e.g., I/O-heavy, Compute-heavy). Because the dominant source of jitter shifts based on system stress (PCIe bus contention for 100GbE networks, NVMe interrupt saturation for disk, execution port starvation for compute), the certifier must conditionally apply the correct jitter penalty models.
*   [ ] **IDE Performance Center Enhancements**: Expand `autumn-ide-plugin` to read live telemetry. The visualization must be rooted at `@InjectTopology`, graphing only what trickles down from that entry point, with dedicated UI tabs for each distinct pipeline (from `@BoundaryChannel` to end).
    *   *Visual Nodes:* Draw interactive graph boxes representing each function and boundary, clearly showing cycle budgets, static limits, and live telemetry data.
    *   *Low-Level Drill-Down:* Provide an interactive breakdown of operations inside each box, mapping Kotlin code down through the compiler target layers. This means showing how the Kotlin IR translates into JVM Bytecode, LLVM Bitcode, ARM64 Assembly (iOS/Android), or x86_64 Assembly (Native Linux), alongside the target-specific micro-architectural cycle cost per operation.
*   [ ] **Formalize the Cold Path**: Finalize the `@ColdChannel` abstraction to allow the hot-path to offload blocking I/O, logging, and DB persistence without inducing GC pressure or OS context switches.

## Phase 2: Network & I/O Primitives
**Goal:** Provide zero-allocation interfaces to the outside world.
*   [ ] **UDP / Multicast `@BoundaryChannel`**: Native integrations for market data feeds (e.g., ITCH) and fast-path multicasting.
*   [ ] **TCP `@BoundaryChannel`**: Lock-free, zero-copy TCP stream reconstruction and parsing.
*   [ ] **Hardware Abstraction Layer (HAL)**: Back these network channels with OS-specific high-performance APIs:
    *   `io_uring` and `epoll` for Linux standard environments.
    *   `kqueue` for BSD/macOS.
    *   `DPDK` for literal kernel-bypass networking.

## Phase 3: The Application Layer
**Goal:** Build practical, usable infrastructure on top of the primitives.
*   [ ] **Autumn HTTP/Web Server**: A fully zero-allocation, purely state-machine-driven web server built on top of the Autumn TCP channels, capable of millions of requests per second per core.
*   [ ] **TLS/HTTPS Support**: Integrate zero-allocation cryptography buffers.
*   [ ] **Filesystem `@BoundaryChannel`**: Zero-copy structured logging and WAL (Write-Ahead Logging) for databases/order books.

## Phase 4: Backlog / Moonshots
**Goal:** Push the boundaries of computer science.
*   [ ] **Kotlin-to-RTL (SystemVerilog) HLS**: Compile Autumn directly to FPGAs/ASICs using Verilator and Yosys (ADR-0025).
