# ADR 0029: Universal FSM Device Boundary via BoundaryChannel

## Status
Accepted

## Context
Standard commodity software models I/O (Network, SSD, Database, and UI communication) as asynchronous blocking operations. Frameworks attempt to mask this latency via Coroutines, Promises, or Async/Await paradigms. However, these mechanisms inherently require:
1. Context switching by the OS or a user-space scheduler.
2. Continual heap allocations for managing suspended states and closures.
3. Unpredictable execution jitter, completely breaking structural execution bounds.

In parallel with our work on compiler-level cycle certification (`autumn-certifier`) and our existing deterministic routing (`HashRouter` feeding lock-free `Arbiters`), any internal use of asynchronous primitives destroys the theoretical proof. We cannot cryptographically certify maximum cycle execution bounds if a thread can yield context to the OS waiting for a TCP packet or an SSD block read.

We need a unified software abstraction that precisely mirrors how high-performance hardware components (FPGAs, ASICs) respond to external physical stimuli without disrupting their internal clock domains.

## Decision
We abstract **all** external, non-deterministic I/O behind a singular, universal interface: the **Boundary Channel FSM Device Boundary**.

Instead of interacting with network sockets or disks directly, the business logic is modeled entirely as a mathematical Finite State Machine (FSM). 
1. **The Boundary as a Ring Buffer:** The `BoundaryChannel` is defined strictly as a memory-mapped, zero-copy ring buffer.
2. **The FSM Paradigm:** The core application logic does not initiate or "await" fetches. It strictly reacts. When the `BoundaryChannel` notifies the engine that memory is populated, the FSM transitions to its next state evaluating purely against CPU L1/L2 caches, completely isolated from external latency.
3. **Sharding Compatibility:** This model plugs directly into our existing `HashRouter`. The channel acts as the entry condition, the router shards the payload, and drops it into a lock-free Single-Producer-Single-Consumer (SPSC) queue pinned to a specific core.

## Consequences

### Positive
- **Guaranteed Deterministic Threading:** Because the application never requests I/O mid-execution, we completely eliminate thread pausing, lock contention, and OS-level context switching within the hot path.
- **Portability for Spin-Off Tools:** A specific component (e.g., an ITCH 5.0 feed handler) written for Autumn has absolutely zero network stack code. It merely reads from a `BoundaryChannel`. We can sell or reuse that exact same business FSM for reading packets off a NIC (via `AF_XDP`) or reading historical logs off an NVMe drive (via `io_uring`), without altering a single byte of business logic.
- **Trivial Certification Mocking:** The `autumn-certifier` can validate software limits easily because simulating network saturation is simply writing bytes sequentially to the shared memory block, isolating the logic layer's physical profiling.

### Negative
- **Paradigm Shift:** Development teams must unlearn "request/response" asynchronous models and structure all behavior as event-driven, tick-based FSMs. 
- **Tooling Burden:** Dealing with FSM debugging requires robust tooling, making the visual topology nodes of the Autumn IDE Performance Center mandatory rather than optional.
