# 31. HFT Kubernetes via Zero-Copy Mmap Multicasting

Date: 2026-07-01

## Status

Accepted

## Context

Phase 5.5 established that Autumn functions cleanly as a Cloud-Native architecture using standard `java.nio` and POSIX UDP polling. However, traditional microservice scaling within cloud ecosystems (like Kubernetes) comes with severe latency penalties. When scaling standard microservices, engineers use Kafka, Redis, or pure gRPC/TCP sockets to stream data between pods. Every single network hop in these models incurs an OS context switch, kernel-space payload copying, and queue serialisation blocking ("backpressure"). 

For High-Frequency Trading (HFT) and ultra-low-latency real-time applications, this multi-process architecture is traditionally abandoned in favor of single-process C++ "Monoliths." Monoliths are fast because they pass raw pointers in RAM, avoiding all IPC overhead. Yet, monoliths pose massive operational risks (e.g. if the logging thread hits a segfault, the trading execution engine is destroyed with it).

Autumn aims to capture the speed of a Monolith while retaining the crash-tolerance and scale of Kubernetes microservices.

## Decision

We will implement **Shared Memory (`mmap`) Multicasting** as a formal Pluggable Boundary Transport using the `@IpcGateway` annotation. 

Instead of treating distinct application processes as distinct VMs sending packets via `IPC` loops, Autumn treats the Kubernetes Node (or Bare-Metal server) as a single physical System-on-a-Chip (SoC). 

Using POSIX `shm_open()` against the `/dev/shm` temporary filesystem block (which Kubernetes transparently supports as RAM via `emptyDir` volumes backed by `Memory`), Autumn applications will memory-map (`mmap()`) standard FSM boundary arrays directly into shared CPU RAM.

- **The Hot Process (e.g., Matching Engine):** Writes raw FSM primitive outcomes sequentially into the `mmap` `AutumnMemoryBank` block. 
- **The Cold Processes (e.g., Risk, Logger, UI):** Execute completely separate executables in isolated Kubernetes Pods. These pods simply read the `/dev/shm` mapped layout by tracking the lock-free indices (`PROT_READ`).

### Architectural Pacts:
1. **Absolute Zero Backpressure:** The writing process (Hot Path) does not know, or care, if consumers exist. It is a mathematical impossibility for a stalled downstream microservice to throttle the execution engine's throughput. 
2. **Absolute Zero Copy:** Because all FSM primitive states are flushed into `mmap` as flattened C-arrays via the `AutumnMemoryBank`, the exact silicon bytes the Engine wrote act as Native Pointers in the Loggers memory address space. No sockets, no OS copying.

## Consequences

- **HFT Kubernetes is Viable:** Trading firms can utilize standard Cloud tooling (Helm, Kubernetes, K9s) for deploying robust, compartmentalized multi-process fleets without sacrificing deterministic hardware speeds.
- **Fail-Safe Crash Tolerance:** A Risk engine crashing from a segfault merely terminates a single reader's `mmap` session; the trading engine (`writer`) remains entirely undisturbed on its pinned core.
- **Observability Parity:** External `autumn-observatory` Sidecar Pods can passively read execution telemetry without injecting a single CPU trace hook into the Hot application, eliminating Heizenbugs entirely.