# ADR-0010: Configuration-Derived Allocation Budget and Compiler Enforcement

## Status

Proposed

## Date

2026-06-18

## Context

Autumn's architecture is built around zero-allocation hot paths, pre-allocated pools, and pointer-free data layouts. The remaining risk is regression: a future code change can introduce hidden allocations in a hot path, and this may only appear under load. Runtime profiling can detect regressions, but it is late in the cycle. Autumn needs an earlier and stronger guarantee: derive the allocation budget from configuration and reject code at compile time when it exceeds that budget.

The configuration already declares the structures that determine memory shape: list page sizes, form slot counts, resource table sizes, route table sizes, and country-specific mappings. This information is sufficient to compute bounded pool sizes ahead of runtime. Autumn therefore adopts a hard memory-bounded model: runtime data memory must stay within configuration-derived pool capacity. The runtime still has long-lived objects that must exist (event loops, caches, HTTP client, resolver chain, IoC container), so the model must distinguish allowed long-lived allocations from forbidden hot-path allocations.

Autumn also targets AI-assisted development workflows. A compiler plugin turns allocation rules into machine-checkable constraints with deterministic diagnostics. This is more AI-friendly than style guides or review comments: an AI agent gets immediate, structured feedback from the compiler about exactly which allocation rule was violated and where.

Scope is explicit: this contract applies to Autumn framework internals (modules such as `autumn-core`, `autumn-state`, `autumn-config`, and related runtime infrastructure). It does not require consuming application code to adopt no-allocation constraints unless the application explicitly opts in.

## Decision

Autumn introduces a configuration-aware allocation contract with compiler-plugin enforcement.

- Build step 1 generates a memory schema from configuration. The schema contains deterministic bounds for each pool category (list hot tier slots, list cold cache ring size, form slot pools, route table entries, resource registry entries, resolver table entries).
- Configuration fields that drive memory shape are explicit budget sources. They may be declared in JSON schema directly or in typed config classes using source annotations such as `@BudgetSource("activePagesOnScreen")`, `@BudgetSource("bufferPages")`, `@BudgetSource("slotsPerPage")`, and `@BudgetSource("slotBytes")`.
- Build step 1a generates SBE layout constants from configuration for each table/object kind: field offsets, slot byte size, alignment padding, unknown-field blob capacity, and slot count.
- Build step 1b computes total pool bytes per category using generated formulas: `slotBytes = fixedFieldBytes + padding + unknownBlobBytes`; `poolBytes = slotBytes × slotCount`. Generated code fails the build if any formula overflows the selected integer width.
- For paginated lists, generated code derives hot-tier capacity from configuration at build time, not runtime. Example: `hotSlotCount = (activePagesOnScreen + 2 × bufferPages + 2 × evictionBoundaryPages) × slotsPerPage`; `hotPoolBytes = hotSlotCount × slotBytes`.
- Build step 2 runs a Kotlin compiler plugin that reads the generated schema and enforces one default rule inside Autumn framework modules: allocations are disallowed outside configuration-derived pools.
- Build step 3 generates and wires the pools directly from schema values. Autumn runtime code does not declare allocation budgets manually; it consumes generated pool access APIs whose capacity is already fixed by configuration.
- Data objects in Autumn runtime paths are allocation-banned at runtime. They must be represented as pool-backed views, slot indices, or raw buffer offsets (ADR-0010 through ADR-0012). Creating heap-backed per-item/per-document data objects in Autumn runtime paths is a compile error.
- `@LongLived` is the only explicit exception annotation. It marks objects that are allowed to allocate exactly once during startup or IoC container initialization (ADR-0009), such as event loop dispatchers, HTTP client instances, and resolver chains.
- The plugin constructs an allocation effect graph from each Autumn entrypoint (request path, frame path, list bind path, form input path). Any allocation site reachable from these paths is a compile error unless it is reachable only through a startup path annotated `@LongLived`.
- Calls into unknown code are unsafe by default. If the plugin cannot prove that a callsite does not allocate or is not `@LongLived`-only, compilation fails.
- Any `@LongLived` allocation reachable from per-request, per-frame, per-item, or per-keystroke paths is a compile error.
- The plugin emits structured diagnostics (stable error codes plus machine-readable metadata identifying the violated rule, path category, and offending symbol). This allows AI tools to apply deterministic fixes instead of relying on natural-language interpretation.
- The contract is target-aware. For each platform, the plugin validates language-level allocations in common code and platform code. Runtime-level allocations outside Kotlin control (system frameworks, GPU buffers, browser internals) are out of scope and verified by benchmark gates, not compile analysis.
- Startup performs a hard validation pass: generated `poolBytes` must match allocated arena capacity exactly for every category. Mismatch is a startup failure, not a warning.

## Developer feedback model

- Enforcement is compile-time and IDE-time. The same compiler plugin checks run during incremental IDE analysis (K2), so violations are underlined in the editor before a full build.
- Command-line builds and CI run the identical checks, ensuring IDE and CI diagnostics stay consistent.
- This is pre-runtime enforcement: allocation violations are rejected before app launch, not discovered only by profiling.

## Consequences

- Memory budgets for Autumn internals become deterministic outputs of configuration, not runtime guesses.
- Runtime memory for Autumn data objects is hard-capped by configuration-derived pool sizes.
- Hot-path allocation regressions in Autumn internals are caught at compile time instead of during profiling.
- Long-lived object allocations are documented and auditable through a single annotation (`@LongLived`), making startup memory cost explicit.
- Autumn maintainers do not manage allocation contracts manually. Configuration defines pool bounds; generated code and compiler checks enforce them.
- The model remains practical: startup and infrastructure allocations are allowed through `@LongLived`, while Autumn runtime hot paths remain strict by default.
- AI-generated changes to Autumn internals become safer by default because allocation violations fail fast with deterministic compiler output, not late-stage profiling surprises.
- Consuming application code remains unconstrained by default and may opt in to the same checks if desired.
- The plugin cannot prove allocations in opaque third-party binaries without contracts; wrappers or adapter contracts are required.
- Runtime verification is still required for non-Kotlin allocations and platform framework behaviour. ADR-0010 complements, not replaces, benchmark-based proof.
- Future ADRs or module APIs may refine `@LongLived` usage rules, effect graph algorithm, and CI failure format.
