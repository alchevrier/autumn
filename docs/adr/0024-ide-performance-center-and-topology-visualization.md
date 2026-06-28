# ADR 0024: IDE Performance Center and Topology Visualization

## Status
Proposed

## Context
Autumn's "circuit-based programming" model successfully enforces exact hardware constraints (cycle budgets, L1 cache limits, flat SoA memory alignments) via the Kotlin K2 compiler plugin (IR rewriting and FIR validation). However, traditional software development relies on runtime profiling (e.g., JFR, perf, Grafana) to assess latency—a feedback loop measured in minutes or hours. 

Because Autumn's constraints are statically proven at compile time using K2's Intermediate Representation, the compiler knows the exact temporal and spatial footprint of the software before it ever executes. Furthermore, the compiler statically guarantees the exact path of dataflow (the pipeline topology) by resolving `@Observe`, `@Speculative`, and `AutumnChannel` boundaries into an unrolled arbiter loop.

We need a mechanism to surface this static topological and temporal analysis directly to the developer instantly during the writing phase, mimicking the immediate hardware routing and timing summary feedback found in FPGA synthesis tools (e.g., Xilinx Vivado).

## Decision
We will construct an `autumn-ide-plugin` (targeting IntelliJ / Kotlin Language Server) that hooks directly into the K2 Front-end Intermediate Representation (FIR) stream. It will provide the following real-time capabilities to developers as they type:

1. **Inline Temporal & Spatial Lenses:**
   Above any function participating in the Autumn pipeline, the IDE will display non-intrusive lens hints showing real-time utilization against declarative hardware budgets:
   `⚡ 16 / 40 Cycles` | `📦 12 / 64 Bytes L1`

2. **Real-time Budget Violations:**
   When an AST mutation conceptually breaches the defined `@CycleBudget` or `@ThreadCacheBudget` boundaries (like instantiating a class that requires heap allocation or adding a heavy branching instruction), the plugin will instantly paint a red syntax error on the offending node prior to compilation taking executing.

3. **Topology Rendering and Channel Surfacing:**
   A dedicated "Autumn Performance Center" tool window will graph the dataflow topology of the entire module. Since standard object graphs and pointers don't exist under Autumn's SoA memory map, developers need visibility into the structural data routing.
   - The plugin will visually map the pipeline: `<BoundaryChannel>` → `@Observe` (Handler A) → `<ColdChannel>` → `@Observe` (Handler B).
   - Right-clicking an `@Observe` function will allow the developer to instantly trace upstream to the specific channel source triggering it, circumventing standard "Find Usages" blind spots inherently present when frameworks abstract routing dynamically.

4. **Execution Port Simulator (ILP Pressure Analysis):**
   Providing raw cycle counts exposes temporal depth, but hardware executes superscalar streams.
   - The plugin will simulate instruction-level parallelism (similar to LLVM Machine Code Analyzer `llvm-mca`) against the projected native translation.
   - It will map the IR down to execution port usage (e.g., ALU vs. AGU utilization), highlighting if a pipeline is bottlenecked on instruction ports while others sit idle natively, thereby guiding the developer towards loop-unrolling or spatial layout adjustments.



5. **Target Platform & Core Topography Awareness:**
   - Developers can select their deployment hardware target (e.g., Intel Skylake, AMD Zen 4) and total core count directly within the IDE plugin.
   - Using this selected profile, the Performance Center will visualize core-to-channel pinning, identify cross-core bottlenecks, and project NUMA/L3 cache latency penalties instantly as they code.
   - This ensures simulated execution metrics flawlessly match the actual deployed production environment conditions.

## Rationale
- **The FPGA Hardware Model:** Building deterministic systems requires developers to view their logic conceptually as circuits constrained natively by physical limits. By elevating cycles and L1 cache sizes to the status of IDE syntax errors, developers are forced to think spatially.
- **Unified Feedback Loop:** Waiting for a pipeline benchmark to crash or report latency is incredibly inefficient. Surfacing the `CycleBudgetVisitor` and `ThreadCacheBudgetVisitor` outputs live against the IDE text buffer short-circuits the pipeline from hours down to milliseconds.
- **Topology Transparency:** Without dynamic references, visualizing the implicit cross-thread routing architecture requires the compiler to surface the static execution sequence it assembled.

## Consequences
- Requires hooking into JetBrains IDE APIs and K2 FIR plugin models.
- Re-evaluating FIR properties in real-time requires the analysis engine (cycle mapping math) to be incredibly lightweight so the syntax highlighter doesn't bottleneck typing performance natively in the IDE.
- Will necessitate centralizing the K2 structural evaluation logic out of the `autumn-compiler-plugin` into an agnostic `autumn-compiler-shared` artifact that both the Gradle compilation phase and the IDE plugin can consume symmetrically.
