# 15. Kotlin Multiplatform Unification for Universal Circuit Programming

Date: 2026-06-21

## Status
Accepted

## Context
Following the establishment of Autumn as an HFT-capable framework natively targeting LLVM (ADR 0008), a strategic question arose: *To achieve ultra-performance uniformly across all device targets (servers, iOS, Android, Web), should the framework abandon the JVM entirely and exclusively mandate Kotlin/Native?*

While Kotlin/Native provides bare-metal execution, strict memory boundaries, and zero-copy POSIX primitives natively, enforcing it across all ecosystems introduces platform-specific friction—specifically on Android and large-scale Cloud setups where the native ecosystems fundamentally rely on JVM byte-code paradigms.

## Decision
We officially establish a **Kotlin Multiplatform (KMP) `commonMain`** strategy for the execution of "Circuit-Based Programming". The core engine (FSMs, lock-free ring-buffers, flat primitive SoA structures, and strictly budgeted arrays) will be maintained in pure Kotlin `commonMain`.

Rather than abandoning the JVM, we leverage the zero-allocation/primitive-array structure to achieve ultra-performance gracefully across the appropriate backend compiler targets. Crucially, the compiler plugin applies **Platform-Specific Graceful Degradation** for hardware features (like IRQ eviction) and strictly translates the Hardware Channel Taxonomy (`@NetworkChannel`, etc.) into the highest-performance APIs available on locked-down consumer OSs.

1. **Kotlin/Native (LLVM) for Linux HFT (Bare-Metal):**
   - **Mechanism:** Compiles to raw Linux machine code (`linuxX64`). The compiler automatically maps `@NetworkChannel` to native Linux Zero-Copy DMA (`AF_XDP`/`io_uring` handled via non-blocking `epoll`). It completely bypasses bloated userspace networking stacks like DPDK. Because the clock-tick scheduler instantly processes incoming packets, the ingress queue never backs up, entirely nullifying the need for Linux `HugePages` management. It generates `/dev/shm` IPC Shared Memory for `@SharedChannel`, alongside `pthread` core pinning and GRUB `isolcpus` hooks for absolute zero-jitter execution.

2. **Kotlin/Native (LLVM) for Apple (iOS/macOS):**
   - **Mechanism:** Compiles to native Mach-O binaries. iOS has no NIC DMA kernel-bypass. Thus, `@NetworkChannel` elegantly degrades to non-blocking OS kernel sockets (via `kqueue` or Apple `Network.framework`). Also, since absolute thread pinning is forbidden, `@ExecutionNode` degrades into high-priority Grand Central Dispatch (GCD) QoS queues. There is no IRQ eviction, but the zero-allocation pipeline still bypasses objective-C/Swift ARC entirely for flawlessly stable 120fps UI rendering.

3. **Kotlin/JVM (ART) for Android:**
   - **Rationale:** Android's host API and UI toolkit (Jetpack Compose) live in the Android Runtime (ART). Executing in pure Native requires a costly JNI (Java Native Interface) bridge.
   - **Mechanism:** Autumn compiles the FSM into JVM bytecode. `@NetworkChannel` degrades to Android's underlying `java.nio` non-blocking socket selectors (which read raw OS socket bytes directly into off-heap `ByteBuffer` structures without object generation). Thread-pinning degrades to Android's high-priority Looper/Handler threads. Dalvik/ART GC pauses never trigger, achieving C++ tier stability without root kernel access.

4. **WebAssembly (WASM) for the Web:**
   - **Mechanism:** Modern browsers restrict memory. The compiler plugin detects the `wasm32` target and elegantly degrades `@NetworkChannel` into WebSockets mapped directly into contiguous JavaScript `Uint8Array` views. Likewise, inter-core annotations (like `@RegisterChannel`) translate into `SharedArrayBuffer` structures passing primitives between Web Workers, unlocking lock-free execution while playing completely within browser security sandbox limits.

5. **Kotlin/JVM (HotSpot) for Cloud Backends:**
   - **Rationale:** The modern JVM JIT excels at dynamic method in-lining yielding massive peak throughput.
   - **Mechanism:** Autumn eliminates the JVM's sole bottleneck (GC pauses). This gives cloud backends deterministic tail-latency with HotSpot throughput optimizations, mapping threads to standard JVM thread pools when run in containerized (Docker/K8s) environments where bare-metal OS partitioning isn't possible.

## Consequences
- **Positive:** We achieve a write-once, deploy-anywhere architectural standard that maintains sub-microsecond tail latencies natively.
- **Positive:** We eliminate the "JNI tax" on Android while matching Swift's absolute bare-metal UI performance on iOS.
- **Negative:** Framework maintainers must be highly disciplined not to introduce JVM-only dependencies (e.g., `java.util.concurrent`) into `commonMain`, relying purely on cross-platform primitive mechanics.