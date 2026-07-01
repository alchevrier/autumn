# 30. Pluggable Layered Boundary Transports

Date: 2026-07-01

## Status

Accepted

## Context

Recent development phases (Phase 4 and upcoming Phase 5) have heavily optimized code for ultra-low latency bare-metal Linux environments. We successfully parsed millions of NASDAQ ITCH 5.0 messages per second by bypassing JVM GC and using native POSIX C-interop constructs. We additionally outlined zero-copy IPC configurations using `mmap` and `/dev/shm` alongside kernel-bypass (e.g., `AF_XDP`). 

While this hardware-sympathetic topology proves the extreme limits of the Autumn architecture (Clock-Aware Programming), it unintentionally isolates the framework. If Autumn inherently requires `AF_XDP`, root privileges, customized NIC drivers, or specific Linux memory partitions to function, it fundamentally prevents the framework from being deployed in standard cloud environments (AWS, GCP, Kubernetes, Docker) or mobile/desktop application spaces.

Autumn's primary architectural advantage is that the core Business Logic (Finite State Machine + `AutumnMemoryBank`) is entirely decoupled from the OS. We need to formalize support for classical network layers so that we do not close ourselves off to the broader commercial market.

## Decision

We will implement a **Tiered Pluggable Boundary Architecture** for `@BoundaryChannel`s within the `autumn-resolver` module. 

Because the central pure FSM never blocks and only reads from pre-allocated flat memory structures (`HashRouter` / `SPSCRingBuffer`), the transport layer populating those buffers can be entirely swapped out as a compile-time/deployment decision without modifying the business logic.

We formally define the following Boundary Tiers:

1. **Cloud-Native / Multiplatform Tier (Default)**
   - **Mechanism:** Standard POSIX non-blocking BSD sockets (`epoll`), standard UDP/TCP streams, and standard Kotlin Multiplatform I/O.
   - **Target:** Kubernetes (K8s), AWS EC2, GCP, Docker, Android, iOS.
   - **Profile:** Microsecond-level latency. Safe, sandboxed, runs on virtualized hardware without requiring root access.

2. **Bare-Metal Tier (Hardware Accelerated)**
   - **Mechanism:** Kernel-bypass routing (`AF_XDP`), `io_uring`, and strict `/dev/shm` IPC.
   - **Target:** High-Frequency Trading (HFT), Real-Time Operating Systems (RTOS), deeply embedded edge devices.
   - **Profile:** Nanosecond-level latency (sub-100ns). Requires `linuxX64`, root privileges, and hardware-affinity limits. 

3. **Offline / Audit Tier (Backtesting & Certification)**
   - **Mechanism:** Direct POSIX file reads (`fread()`) iterating over PCAP files or historical binary buffers.
   - **Target:** Local CI/CD pipelines, K2 compiler cycle limit benchmarking, ML model training.
   - **Profile:** Maximum throughput (multi-million msgs/sec) batching.

## Consequences

- **Open Market Reach:** The framework remains highly viable for general high-performance microservices and mobile applications, ensuring we maintain Autumn's identity as a versatile Kotlin Multiplatform tool.
- **Hardware Scalability:** Developers can build business logic locally on macOS or standard cloud servers, then simply swap the transport dependency matrix when building the `linuxX64` executable to instantly unlock `AF_XDP` nanosecond accelerations, without rewriting a single line of business logic.
- **Workload Shifts:** Moving forward, standard TCP/UDP implementations in `autumn-resolver` must be treated as first-class citizens alongside the highly-optimized bare-metal paths.
