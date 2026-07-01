# 32. Centralized Hardware Orchestration via Master Node

Date: 2026-07-01

## Status

Accepted

## Context

Phase 5 introduced Zero-Copy `mmap` Multicasting (ADR-0031), enabling separate Autumn processes to share exact physical memory spaces by memory-mapping `/dev/shm` boundaries. While this solves the pod-to-pod latency and backpressure problem historically found in cloud-native microservices, it surfaces a new operational challenge: **Hardware Topologies.**

In a typical Kubernetes cluster, pods are scheduled dynamically based on CPU quotas and memory availability. The scheduler treats all CPU cores equally. However, Autumn relies heavily on `linuxX64` thread-pinning (`NativeClock.pinToCore`) and L1/L3 cache architectures. 

If we run 6 different Autumn microservices (e.g., Engine, Risk, Logger, Feed Handler A, Feed Handler B, UI) on a 16-core physical worker node, we cannot allow standard Kubernetes isolation features (like CFS bounds) to dynamically spin our pinned loops on and off, nor can we blindly write to `/dev/shm` without coordinating who is reading what.

## Decision

To support HFT (High-Frequency Trading) microservice topologies inside Kubernetes, we establish the concept of the **Autumn Master Orchestrator Node**.

Rather than utilizing scattered standalone binaries, Autumn deployments in bare-metal / HFT-Kubernetes clusters will be coordinated by a central orchestrator.

1. **Topology Injection:** 
   The Master Orchestrator reads a single, unified declarative manifest (e.g., `autumn-fleet.yaml`) which describes the FSM connections between completely separate executables.

2. **Core Allocation & Affinity (isolcpus):** 
   The Orchestrator acts as a control plane for the physical hardware. It ensures that the absolute Hot Path (e.g., the Matching Engine executing `@CycleBudget` ticks) is dynamically assigned to isolated, reserved OS cores (cores 2-6) while assigning Cold Path processes (Loggers) to generic scheduler cores (cores 0-1). It binds `NativeClock.pinToCore()` dynamically on boot, acting as a lightweight RTOS supervisor.

3. **Shared Memory (`mmap`) Provisioning:**
   The Master Node physically provisions and zeros-out the `/dev/shm/` blocks *before* spinning up the worker applications. It then passes the file descriptors and physical bounds via ENV pointers into the microservices on boot.

4. **Lifecycle Control:**
   Because all communication happens synchronously through memory maps without sockets, microservices cannot "discover" each other via IP addresses. The Orchestrator dictates exactly when applications can safely `mmap` and start polling. 

## Consequences

- **Hardware as Code:** The Kubernetes Deployment YAML combined with standard `topology.json` K2 telemetry entirely defines a physical data-center architecture.
- **Microservices acting as an SoC:** The Master Orchestrator treats the distributed container fleet not as separate computers, but exactly like physical components attached to a single embedded System-on-a-Chip motherboard.
- **Shift in Abstraction:** Autumn formally transitions from just being a fast compiler plugin into a comprehensive bare-metal deployment platform, capable of coordinating physical hardware affinity entirely from a control plane.