# ADR 0018: AF_XDP Zero-Copy Network Ingress

## Status
Proposed

## Context
Standard Linux networking is fundamentally incompatible with High-Frequency Trading (HFT) and Time-Triggered Architectures. When a physical packet arrives at the Network Interface Card (NIC):
1. A hardware interrupt stops the CPU.
2. The Kernel executes a `softirq`.
3. The datalink payload is copied into a generic OS `sk_buff` structure.
4. It navigates a monolithic, lock-heavy TCP/IP tracking and routing stack.
5. It is finally copied *again* into the application memory via the `recv()` socket syscall.

This process consumes thousands of CPU cycles, thrashes the L1 cache, and destroys determinism. 

Traditional HFT solutions map the NIC directly to userspace using **DPDK** or **Solarflare OpenOnload**. 
However, **AF_XDP** (eXpress Data Path) is a modern Linux kernel feature (built via eBPF) that allows user-space applications to receive packets directly from the NIC driver *before* they enter the Linux networking stack, without requiring out-of-tree vendor-specific kernel modules like DPDK.

## Decision
Autumn will implement standard `AF_XDP` sockets as the primary implementation of the `@NetworkChannel` topology.

### Architecture Mechanics
`AF_XDP` relies on a shared memory region called the **UMEM**, combined with an **eBPF (Extended Berkeley Packet Filter)** program to route only the relevant traffic to us.

1. **eBPF Traffic Filtering & Decapsulation:** By default, XDP intercepts *everything* on a network queue. To prevent SSH, ARP, or other background noise from polluting our lock-free rings, we attach a compiled eBPF program directly to the NIC hardware hook. This program parses the headers (Ethernet -> IP -> UDP). If it matches our target `NetworkChannel` criteria:
    * It uses `bpf_xdp_adjust_head` to physically strip away all L2/L3/L4 network headers.
    * It returns `XDP_REDIRECT` to send *only the raw application payload* to our AF_XDP socket.
    Everything else returns `XDP_PASS` to gracefully flow to the normal Linux kernel.
2. **Pre-allocation:** Autumn pre-allocates a massive contiguous block of memory (`UMEM`) and divides it into strictly equal frames (e.g., 2048 bytes).
3. **Registration:** By calling standard Linux `bind()` on an `AF_XDP` socket, the Kernel tells the physical NIC hardware where this UMEM block exists in RAM.
4. **Completion/Fill Rings:** Autumn maintains Lock-Free Ring Buffers (using our `HardwareSequence` Acquire/Release semantics natively in C) to communicate with the Kernel.
    * Autumn puts a free UMEM frame index into the `FILL` ring.
    * The NIC physically receives an Ethernet frame off the wire and uses **DMA** (Direct Memory Access) to write the raw stream of electrons straight into that specific UMEM frame in RAM.
    * The Kernel places that index into the `RX` (Receive) ring.

### Integration with Autumn's Flyweights
The Autumn K2 Compiler maps the `@Pipelined` data structures to the `UMEM`.

When the `AutumnScheduler`'s clock ticks, it polls the `RX` ring. Because the eBPF program stripped the network headers, the very first byte of the `RX` frame contains the pure application payload. It evaluates the raw bytes dynamically inside the `UMEM` frame without *ever* copying the data to a Kotlin heap object. The Flyweight `inline value class` simply points to the start of the payload, needing exactly zero knowledge of network protocol offsets, allowing Native structures (like ITCH 5.0) to perfectly align with `@Pipelined` arrays.

## Consequences
*   **Positive:** Pure, unabashed Zero-Copy hardware ingress. Capable of processing 20M+ packets per second on a single core.
*   **Positive (Variance over Peak Speed):** While raw C++ DPDK on proprietary Solarflare NICs might beat AF_XDP by ~1 microsecond in absolute minimum latency, HFT profitability relies on **eliminating variance**. You lose money on tail-latency, not median latency. A system that guarantees a 2.0us response time 99.99% of the time (via strict lock-free polling and Circuit-Based execution) is vastly superior to a complex C++ stack that averages 1.0us but spikes to 15.0us due to branch mispredictions or lock contention.
*   **Positive:** Requires exactly zero Kernel modifications or vendor-locked DPDK drivers. Standard Linux > 5.x handles it natively.
*   **Negative:** Developers must implement binary packet parsing (e.g., FIX, ITCH) manually over byte structures. Heavy C-Interop (`sys/socket.h`, `linux/if_xdp.h`) is required to bridge the UMEM pointers seamlessly into Kotlin's managed memory bounds.
