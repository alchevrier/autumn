# ADR-0005: System-on-a-Chip (SoC) Static Composition Root (Replaces IoC)

## Status

Accepted (Replaces previous Lazy IoC proposal)

## Date

2026-06-20

## Context

Autumn spans multiple modules — core, state, buckets, resolver, config, and UI. Originally, we proposed a shared IoC container with lazy initialization (e.g., passing around a service locator that lazily instantiates dependencies upon first access). 

However, as the framework evolved into a strict **Circuit-Based Programming** model with zero-allocation constraints, lazy initialization became fundamentally incompatible. Lazy initialization implies unpredictable runtime memory allocations occurring on the hot path (when the component is first requested). This violates the deterministic timing and pre-allocated memory matrix constraints of the circuit model. Furthermore, dynamic IoC containers (like Koin) or reflection-based DI frameworks obscure the exact memory layout required by the K2 compiler at compile time.

## Decision

We have scrapped the internal dynamic IoC container and lazy initialization inside Autumn in favor of a **System-on-a-Chip (SoC) Motherboard pattern**.

- The `AutumnMotherboard` serves as the explicit, static composition root for the Autumn ecosystem.
- All memory matrices, bucket pools, string registries, and the Epoch State Engine are physically pre-allocated as `val` properties in the Motherboard right at boot time.
- Annotations like `@LongLived` and `@InjectBudget` allow the K2 compiler plugin to statically verify these allocations at compile time.
- Developers *may* use their own user-side IoC containers (e.g., Dagger, Hilt, Needle) in their application layer to inject the network clients or even the Motherboard itself, but Autumn itself does not provide or rely on one internally.
- Initialization is explicitly *eager* but extremely fast, because it consists almost exclusively of allocating flat `IntArray` and `ByteArray` registers without complex object graph instantiation.

## Consequences

- **Positives:** Predictable memory footprint verified strictly at compile time. No hidden runtime latency penalties or GC pauses due to lazy closures initializing during a user interaction (like clicking a button).
- **Positives:** Reduces the library's overhead. No need for complex Dependency Injection lookup maps or reflection.
- **Negatives:** Developers must explicitly wire their platform-specific modules (like network clients) directly into the `AutumnMotherboard` constructor at boot. This is a deliberate tradeoff favoring predictability and pure circuit mechanics over "framework magic."
