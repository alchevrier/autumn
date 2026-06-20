# autumn

![CI](https://github.com/alchevrier/autumn/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/dev.autumn/autumn-core.svg)

Circuit-based, zero-allocation frontend skeleton for mobile and web.

## What is Autumn?

Autumn is a Kotlin Multiplatform framework built around a **circuit-based programming model** for commodity hardware. It decouples core state logic from platform-specific rendering while enforcing strict memory bounds by design. By bypassing the garbage collector and eliminating pointer indirection, Autumn guarantees deterministic, zero-latency rendering using lock-free arrays and emulated hardware interrupts.

## Core pipeline: Socket to Pixel

- **Autumn Network Engine** pulls raw bytes from OS sockets (or local mocks) without manifesting object graphs.
- **Config & Registry** parses the incoming payloads into pre-allocated, flat integer/byte arrays. 
- **Epoch State Engine** tracks slot mutations and emits a single coalesced wake-up pulse, dropping all intermediate state tearing.
- **Native UI (Compose / SwiftUI)** attaches to the hardware wire, waking up exactly once per batch frame to read directly from the underlying physical registries.

Because the backend is treated as an external commodity out-of-bounds, Autumn does not care *where* the bytes come from—it only guarantees they are rendered instantly, deterministically, and safely once they cross the network layer.

## Key features

- **Array-based, pointer-free data structures**: all internal data — configuration tables, resource registries, list items, and form state — is stored in flat pre-allocated arrays accessed by integer index and byte offset. There are no object graphs, no pointer chains, and no GC-visible references between data items.
- **Circuit-Based Reactivity**: replaces traditional flow observers with a single lock-free `IntArray` state engine, ensuring the UI natively coalesces pulses and never chokes on backpressure.
- **K2 Compiler Enforcement**: physically rewrites the syntax tree at compile-time to enforce hardware partition limits and inject memory boundaries via `@InjectBudget`.
- **Native UI rendering**: keeps rendering close to each platform while executing a fully shared, static execution pipeline.

## Module overview

- `autumn-core` — core interfaces, shared domain models, and compiler pacts (`@LongLived`, `@InjectBudget`).
- `autumn-compiler-plugin` — K2 compiler plugin enforcing strictly bounded allocations at build time.
- `autumn-gradle-plugin` — Gradle hook required to execute the compiler plugin across platforms.
- `autumn-state` — hardware-sympathetic reactivity engine (`EpochStateEngine`) replacing traditional Flow observers.
- `autumn-buckets` — bucket abstractions mapping configuration pointers to raw image/document strings.
- `autumn-resolver` — deterministic network boundary (`AutumnNetworkEngine`) executing in-place handoffs.
- `autumn-config` — zero-allocation payload string registry and hardware matrix limit calculator (`JsonConfigParser`).
- `autumn-ui` — native rendering bridge linking platform Canvas text exactly to byte indices (`AutumnCircuitBinder`, `AutumnMotherboard`).

## Architecture

```text
                        +----------------------+
                        | OS Socket / Network  |
                        | payloads (JSON/Raw)  |
                        +----------+-----------+
                                   |
                                   v
                         +-------------------+
                         | autumn-resolver   |
                         | (Network Engine)  |
                         +---------+---------+
                                   |
                                   v
                         +-------------------+
                         | autumn-config     |
                         | (Zero-alloc parse)|
                         +---------+---------+
                                   |
                                   v
                         +-------------------+
                         | autumn-state      |
                         | (Epoch Interrupt) |
                         +---------+---------+
                                   |
                                   v
                         +-------------------+
                         | autumn-ui         |
                         | (Canvas Binding)  |
                         +-------------------+
```

## Repository structure

```text
docs/adr/
autumn-core/               # Compiler pacts and limits
autumn-compiler-plugin/    # K2 AST visitor enforcing allocations
autumn-gradle-plugin/      # Gradle hooks for plugin injection
autumn-resolver/           # In-place Network Handoff API
autumn-config/             # Zero-alloc JSON parsing and registries
autumn-buckets/            # Content offset mappers
autumn-state/              # Circuit-based Epoch observer
autumn-ui/                 # SoC Motherboard and Native UI Compose Binder
```

## Getting started

Getting started guidance will be added as the project skeleton evolves. For now, this repository establishes the architectural decisions and module boundaries for the framework.

## ADRs

Architectural decisions live in [`docs/adr/`](docs/adr) and capture the initial shape of Autumn:

- ADR-0001 — UI → State + Buckets Pattern
- ADR-0002 — Bucket Source Decoupling (Delegated to Backend)
- ADR-0003 — Remote Configuration Versioning
- ADR-0004 — Interaction Conventions
- ADR-0005 — System-on-a-Chip (SoC) Static Composition Root (Replaces Lazy IoC)
- ADR-0006 — Zero-Allocation JSON Data Model
- ADR-0007 — Paginated List Rendering and GC Reduction
- ADR-0008 — Form State Management via Pre-Allocated Slots
- ADR-0009 — Event Loop Model and Context Switch Minimisation
- ADR-0010 — Configuration-Derived Allocation Budget and Compiler Enforcement
- ADR-0011 — Interaction and Entity Schema Contract
- ADR-0012 — Circuit-Based Data Pipeline and Interrupt Moderation
- ADR-0013 — The Circuit Skeleton as a Commodity Backend Consumer

## Integration example (Jetpack Compose)

Autumn bypasses standard object allocation by replacing DTO flows with an emulated hardware interrupt wire. Here is how you bind Autumn to a Compose UI:

```kotlin
// 1. The Circuit Binder
// This adapts Autumn's memory matrices to platform-specific graphics.
class MyScreenBinder(
    stateEngine: EpochStateEngine,
    stringRegistry: StringRegistry
) : AutumnCircuitBinder(stateEngine, stringRegistry) {
    // Expose specific coordinates statically configured by @InjectBudget
    fun getHeroTitle() = resolveTextPrimitive(coordinateId = 0)
    fun getActionLabel() = resolveTextPrimitive(coordinateId = 1)
}

// 2. The Native UI
@Composable
fun AutumnScreen(binder: MyScreenBinder) {
    // A single state trigger. When the global hardware wire pulses, 
    // this increments, causing Compose to redraw the screen.
    var epochTick by remember { mutableStateOf(0) }
    
    LaunchedEffect(binder) {
        // Suspend the UI completely until the batch finishes
        binder.attachToInterruptWire(this) {
            epochTick++ // Emulates an interrupt wakeup
        }
    }

    // Rely on the tick to trigger recomposition, 
    // then read strictly from the hardware-sympathetic registry
    Column {
        Text(text = binder.getHeroTitle())
        Button(onClick = { /* Fire NetworkHandoff in-place */ }) {
            Text(text = binder.getActionLabel())
        }
    }
}
```

Because Autumn handles the payloads purely natively as bytes, making the network request does not fill the garbage collector. The OS socket bytes sit in `StringRegistry`, the `EpochStateEngine` evaluates the exact slot mutations, and Compose only executes a String allocation inside `resolveTextPrimitive` when drawing the physical pixel!
