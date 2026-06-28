# ADR-0022 — IntelliJ Circuit Visualizer and Compiler Telemetry

## Status
Proposed

## Context
Autumn's architecture revolves around "A Priori Determinism" and "Mechanical Sympathy." Through custom K2 compiler plugins (`TopologySynthesisTransformer`, `PipelinedSoATransformer`), we calculate and enforce global memory boundaries, hardware alignments, sub-30ns cross-thread FSM routines, and L1 cache (`@ThreadCacheBudget`) utilization at compile time. 

However, because this is all done invisibly as Intermediate Representation (IR) tree manipulation, the developer is largely flying blind. The framework behaves as a "magic black box" where physical physics are mathematically enforced but visually hidden until a compilation error is thrown or benchmark metrics are read from the console. 

For developers to truly adopt Circuit-Based Programming, they need to visualize their circuits. Just as hardware engineers have schematics and EDA (Electronic Design Automation) tools, Autumn developers need IDE-level hardware telemetry.

## Decision
We will build a dedicated **IntelliJ IDE Plugin** and establish a standard **Compiler Telemetry Contract**.

1. **Compiler Telemetry Export:** The `autumn-compiler-plugin` will be updated to serialize its global layout computations (struct byte offsets, capacity boundaries, deterministic frame topologies, estimated cycle costs) into a standard artifact (e.g., `build/autumn-topology.json`) during the compilation phase.
2. **IntelliJ Circuit Visualizer:** A companion IntelliJ/Android Studio plugin will read this artifact and surface the mechanical realities directly over the Kotlin code via CodeLens, tooltips, and inspections.
3. **Key Plugin Capabilities:**
   - *Inline Cache Budgets:* Inline UI hints above `@Pipelined` structs detailing byte size and L1 cache consumption.
   - *A Priori Cost Tooltips:* Exposing the static cycle cost / tick bounds of unrolled `@InjectTopology` pipeline sequences.
   - *Dataflow Navigation:* A side-panel or inline graph rendering the exact routing between `@BoundaryChannel`, its shards, and the destination FSM handlers, effectively bypassing standard "Find Usages."
   - *Proactive Squiggles:* Warning developers *before* compilation if an added property structurally overflows the specified `@ThreadCacheBudget` threshold.

## Consequences
- **Positive:** Transforms Autumn from a "fast framework" into a full Hardware Description Environment. Drastically lowers the learning curve of Data-Oriented Design (DOD) by making memory alignment visual and tangible.
- **Positive:** Closes the feedback loop between writing code and understanding its hardware cost.
- **Negative:** Introduces a brand new repository and lifecycle dependency (JetBrains UI plugin development), demanding effort outside of the core compiler math.
- **Negative:** Requires stabilizing the JSON telemetry schema emitted by the K2 plugin, treating it as a public API surface.