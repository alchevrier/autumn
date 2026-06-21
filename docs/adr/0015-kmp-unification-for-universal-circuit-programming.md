# 15. Kotlin Multiplatform Unification for Universal Circuit Programming

Date: 2026-06-21

## Status
Accepted

## Context
Following the establishment of Autumn as an HFT-capable framework natively targeting LLVM (ADR 0008), a strategic question arose: *To achieve ultra-performance uniformly across all device targets (servers, iOS, Android, Web), should the framework abandon the JVM entirely and exclusively mandate Kotlin/Native?*

While Kotlin/Native provides bare-metal execution, strict memory boundaries, and zero-copy POSIX primitives natively, enforcing it across all ecosystems introduces platform-specific friction—specifically on Android and large-scale Cloud setups where the native ecosystems fundamentally rely on JVM byte-code paradigms.

## Decision
We officially establish a **Kotlin Multiplatform (KMP) `commonMain`** strategy for the execution of "Circuit-Based Programming". The core engine (FSMs, lock-free ring-buffers, flat primitive SoA structures, and strictly budgeted arrays) will be maintained in pure Kotlin `commonMain`.

Rather than abandoning the JVM, we leverage the zero-allocation/primitive-array structure to achieve ultra-performance gracefully across the appropriate backend compiler targets:

1. **Kotlin/Native (LLVM) for HFT and Apple (iOS/macOS):**
   - **HFT:** Compiles to raw Linux machine code (`linuxX64`), enabling `/dev/shm` IPC Shared Memory, `pthread` core pinning, and DMA register pointer handoffs.
   - **Apple:** Compiles to native Mach-O binaries. Eliminates the need for any virtual machine on iPhones, bypassing GC for strictly 120fps UI rendering.

2. **Kotlin/JVM (ART) for Android:**
   - **Rationale:** Android's entire host API, drivers, and UI toolkit (Jetpack Compose) live in the Android Runtime (ART). Executing business logic in Kotlin/Native on Android requires crossing a JNI (Java Native Interface) bridge for every event, which ironically introduces severe microsecond-latency penalties on every UI frame.
   - **Mechanism:** By targeting Kotlin/JVM, Autumn executes natively in the ART. Because Autumn operates entirely via pre-budgeted primitives and generates zero garbage objects, the Android Garbage Collector never triggers. It achieves predictable C++ tier stability *without* the JNI bottleneck.

3. **Kotlin/JVM (HotSpot) for Cloud Backends:**
   - **Rationale:** The modern JVM JIT (Just-In-Time) compiler excels at dynamic method in-lining based on live branch-prediction data, offering higher peak throughput than static LLVM in massive routing scenarios.
   - **Mechanism:** Autumn naturally eliminates the JVM's sole bottleneck (GC pauses and heap fragmentation). This gives cloud microservices the deterministic tail-latency of an FPGA with the raw throughput optimization of HotSpot.

## Consequences
- **Positive:** We achieve a write-once, deploy-anywhere architectural standard that maintains sub-microsecond tail latencies natively.
- **Positive:** We eliminate the "JNI tax" on Android while matching Swift's absolute bare-metal UI performance on iOS.
- **Negative:** Framework maintainers must be highly disciplined not to introduce JVM-only dependencies (e.g., `java.util.concurrent`) into `commonMain`, relying purely on cross-platform primitive mechanics.